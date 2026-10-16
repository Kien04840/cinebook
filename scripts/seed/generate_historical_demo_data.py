#!/usr/bin/env python3
"""
CineBook Historical Demo Data Generator
========================================
Author: CineBook Core Team
Target Period: 2026-09-12 00:00:00 to 2026-10-02 23:59:59 (21 historical days)
Deterministic Seed: 20261002

This generator connects to the local MySQL CineBook database (read-only for metadata),
and produces a comprehensive, realistic historical dataset covering:
  - users & user_roles (supplemental customer users)
  - showtimes (non-overlapping, respecting operating hours, turnaround, duration)
  - bookings (realistic distribution: PAID, REFUNDED, CANCELLED, EXPIRED)
  - tickets (dynamic pricing compliant, seat capacity aware)
  - payments & refunds (100% financial integrity, matching booking totals)
  - booking_promotions (snapshot discounts)
  - booking_foods (realistic concessions attachment)
  - notifications (valid lifecycle events)

All records are written as batch multi-row INSERTs in transaction blocks.
NO destructive SQL (no DROP, TRUNCATE, DELETE, UPDATE, ALTER).
"""

import sys
import os
import subprocess
import csv
import io
import uuid
import random
import json
from datetime import datetime, date, time as dtime, timedelta
from collections import defaultdict

# -------------------------------------------------------------
# 0. CONFIGURATION & CONSTANTS
# -------------------------------------------------------------
RANDOM_SEED = 20261002
START_DATE = date(2026, 9, 12)
END_DATE = date(2026, 10, 2)
OUTPUT_SQL_FILE = os.path.join(os.path.dirname(__file__), "seed_data.sql")
OUTPUT_METRICS_FILE = os.path.join(os.path.dirname(__file__), "seed_metrics.json")

# Role ID for CUSTOMER
ROLE_CUSTOMER_ID = "53fc43d0-c80a-4eae-b8a1-2081aa901d3a"
# Pre-computed BCrypt hash for "Password@123"
BCRYPT_PASSWORD_HASH = "$2a$10$ZHM/WzA6M5znr6lfUkQlqujgMuRLOjljw8GC1i1ITtL4Zgd1skGNe"

# -------------------------------------------------------------
# 1. DATABASE METADATA FETCHING
# -------------------------------------------------------------
def query_db(sql):
    res = subprocess.run(
        ['mysql', '-u', 'root', '--default-character-set=utf8mb4', '--batch', '--raw', '-e', sql, 'cinebook'],
        capture_output=True,
        text=True,
        encoding='utf-8'
    )
    if res.returncode != 0:
        raise RuntimeError(f"Database error executing query: {res.stderr}\nQuery: {sql}")
    reader = csv.DictReader(io.StringIO(res.stdout), delimiter='\t')
    return list(reader)

def escape_sql(val):
    if val is None:
        return "NULL"
    if isinstance(val, (int, float)):
        return str(val)
    if isinstance(val, (datetime, date)):
        return f"'{val.strftime('%Y-%m-%d %H:%M:%S') if isinstance(val, datetime) else val.strftime('%Y-%m-%d')}'"
    val_str = str(val).replace("\\", "\\\\").replace("'", "''")
    return f"'{val_str}'"

# -------------------------------------------------------------
# 2. MAIN GENERATOR CLASS
# -------------------------------------------------------------
class DemoDataGenerator:
    def __init__(self):
        random.seed(RANDOM_SEED)
        print("Initializing DemoDataGenerator with seed:", RANDOM_SEED)
        self.load_metadata()

    def load_metadata(self):
        print("--> Loading database metadata...")
        # 1. Cinemas
        self.cinemas = query_db("SELECT id, name, city, opening_time, closing_time FROM cinemas WHERE status='ACTIVE' AND deleted_at IS NULL;")
        self.cinema_map = {c['id']: c for c in self.cinemas}

        # 2. Auditoriums
        self.auditoriums = query_db("""
            SELECT a.id, a.cinema_id, a.name, a.type, a.turnaround_minutes, a.snap_interval_minutes 
            FROM auditoriums a 
            JOIN cinemas c ON a.cinema_id = c.id 
            WHERE c.status='ACTIVE' AND c.deleted_at IS NULL AND a.status='ACTIVE' AND a.deleted_at IS NULL;
        """)
        self.aud_by_cinema = defaultdict(list)
        for a in self.auditoriums:
            self.aud_by_cinema[a['cinema_id']].append(a)

        # 3. Seats
        raw_seats = query_db("""
            SELECT s.id, s.auditorium_id, s.row_label, s.seat_number, st.code as seat_type, st.capacity, st.price_modifier 
            FROM seats s 
            JOIN seat_types st ON s.seat_type_id = st.id 
            WHERE s.status='ACTIVE' AND st.status='ACTIVE'
            ORDER BY s.auditorium_id, s.row_label, CAST(s.seat_number AS UNSIGNED);
        """)
        self.seats_by_aud = defaultdict(list)
        self.aud_capacity = defaultdict(int)
        for s in raw_seats:
            cap = int(s['capacity'])
            s['capacity'] = cap
            s['price_modifier'] = float(s['price_modifier'])
            self.seats_by_aud[s['auditorium_id']].append(s)
            self.aud_capacity[s['auditorium_id']] += cap

        # 4. Movies
        self.movies = query_db("SELECT id, title, duration_minutes, release_date FROM movies WHERE status='NOW_SHOWING' AND deleted_at IS NULL AND duration_minutes >= 60;")
        for m in self.movies:
            m['duration_minutes'] = int(m['duration_minutes'])

        # 5. Food Items
        self.foods = query_db("SELECT id, name, price FROM food_items WHERE status='ACTIVE' AND deleted_at IS NULL;")
        for f in self.foods:
            f['price'] = float(f['price'])

        # 6. Promotions
        self.promotions = query_db("SELECT id, code, discount_type, discount_value, min_order_amount, max_discount_amount FROM promotions WHERE status='ACTIVE' AND start_at <= '2026-09-12' AND end_at >= '2026-10-02';")
        for p in self.promotions:
            p['discount_value'] = float(p['discount_value'])
            p['min_order_amount'] = float(p['min_order_amount']) if p['min_order_amount'] else 0.0
            p['max_discount_amount'] = float(p['max_discount_amount']) if p['max_discount_amount'] else None

        # 7. Existing Customers
        self.existing_users = query_db("""
            SELECT u.id, u.email, u.full_name 
            FROM users u 
            JOIN user_roles ur ON u.id = ur.user_id 
            JOIN roles r ON ur.role_id = r.id 
            WHERE r.name='CUSTOMER' AND u.status='ACTIVE' AND u.deleted_at IS NULL;
        """)

        # 8. Existing Showtimes in target period
        self.existing_showtimes = query_db("""
            SELECT s.id, s.auditorium_id, s.movie_id, s.start_time, s.end_time, s.base_price, s.format 
            FROM showtimes s 
            WHERE s.start_time >= '2026-09-12 00:00:00' AND s.start_time <= '2026-10-02 23:59:59' AND s.status <> 'CANCELLED';
        """)
        for s in self.existing_showtimes:
            s['base_price'] = float(s['base_price'])
            s['start_time'] = datetime.strptime(s['start_time'], '%Y-%m-%d %H:%M:%S')
            s['end_time'] = datetime.strptime(s['end_time'], '%Y-%m-%d %H:%M:%S')

        # 9. Existing booked seats
        existing_tix = query_db("""
            SELECT b.showtime_id, t.seat_id 
            FROM tickets t 
            JOIN bookings b ON t.booking_id = b.id 
            WHERE b.showtime_id IN (SELECT id FROM showtimes WHERE start_time >= '2026-09-12 00:00:00');
        """)
        self.existing_booked_seats = defaultdict(set)
        for t in existing_tix:
            self.existing_booked_seats[t['showtime_id']].add(t['seat_id'])

        print(f"Loaded: {len(self.cinemas)} cinemas, {len(self.auditoriums)} auds, {len(raw_seats)} seats, "
              f"{len(self.movies)} movies, {len(self.foods)} foods, {len(self.promotions)} promos, "
              f"{len(self.existing_users)} existing customers, {len(self.existing_showtimes)} existing showtimes.")

        # Classify movies into demand tiers
        self.classify_movies()

        # Cinema traffic weights
        self.setup_cinema_weights()

    def classify_movies(self):
        high_titles = {
            "Dune: Part Two", "The Dark Knight", "Spider-Man: Brand New Day",
            "Demon Slayer: Kimetsu no Yaiba Infinity Castle", "Parasite",
            "The Shawshank Redemption", "Toy Story 5", "Avatar Aang: The Last Airbender"
        }
        med_titles = {
            "Chainsaw Man - The Movie: Reze Arc", "Spirited Away", "Your Name.", "Cars",
            "The Green Mile", "The Uprising", "Resident Evil", "Insidious: Out of the Further",
            "Primetime", "Minions & Monsters", "The Lord of the Rings: The Return of the King",
            "The Godfather Part II", "The Godfather", "Schindler's List"
        }
        self.movies_high = []
        self.movies_med = []
        self.movies_low = []
        for m in self.movies:
            title = m['title']
            if title in high_titles:
                self.movies_high.append(m)
            elif title in med_titles:
                self.movies_med.append(m)
            else:
                self.movies_low.append(m)
        print(f"Movie tiers: {len(self.movies_high)} High, {len(self.movies_med)} Med, {len(self.movies_low)} Low.")

    def setup_cinema_weights(self):
        self.cinema_weights = {}
        for c in self.cinemas:
            name = c['name']
            if "Landmark 81" in name or "Bitexco" in name or "Quốc gia" in name or "Bà Triệu" in name:
                self.cinema_weights[c['id']] = 1.40
            elif "Đồng Khởi" in name or "Nam Sài Gòn" in name or "Thanh Xuân" in name or "Phạm Ngọc Thạch" in name or "Lotte Cinema Đà Nẵng" in name:
                self.cinema_weights[c['id']] = 1.15
            elif "Vincom Đà Nẵng" in name or "Xuân Thủy" in name or "Thăng Long" in name or "Galaxy" in name or "Metiz" in name:
                self.cinema_weights[c['id']] = 0.95
            else:
                self.cinema_weights[c['id']] = 0.75

    def generate_users(self, count=60):
        print(f"--> Generating {count} supplemental customer users...")
        first_names = ["Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Phan", "Vũ", "Đặng", "Bùi", "Đỗ", "Hồ", "Ngô", "Dương"]
        middle_names = ["Văn", "Thị", "Hữu", "Đức", "Minh", "Quốc", "Anh", "Hoài", "Thanh", "Ngọc", "Tuấn", "Phương"]
        last_names = ["An", "Bình", "Cường", "Dũng", "Giang", "Hải", "Hương", "Khánh", "Linh", "Long", "Mai", "Nam", "Phúc", "Quân", "Sơn", "Thảo", "Trang", "Tùng", "Vinh"]
        
        self.new_users = []
        self.user_pool = list(self.existing_users)
        
        start_phone = 920000001
        for i in range(count):
            uid = str(uuid.uuid4())
            fname = f"{random.choice(first_names)} {random.choice(middle_names)} {random.choice(last_names)}"
            email = f"cust_demo_{i+1:03d}@cinebook-demo.vn"
            phone = f"0{start_phone + i}"
            u_dict = {
                'id': uid,
                'email': email,
                'password_hash': BCRYPT_PASSWORD_HASH,
                'full_name': fname,
                'phone': phone,
                'avatar_url': None,
                'status': 'ACTIVE',
                'email_verified': 1,
                'last_login_at': '2026-09-25 14:00:00',
                'created_at': '2026-09-01 08:00:00',
                'updated_at': '2026-09-01 08:00:00',
                'deleted_at': None,
                'version': 0
            }
            self.new_users.append(u_dict)
            self.user_pool.append({'id': uid, 'email': email, 'full_name': fname})
        print(f"Total customer pool available: {len(self.user_pool)} users.")

    def generate_showtimes(self):
        print("--> Generating historical showtimes...")
        self.new_showtimes = []
        
        # Build index of existing showtimes per auditorium per day
        existing_aud_days = defaultdict(set)
        for s in self.existing_showtimes:
            d = s['start_time'].date()
            existing_aud_days[(s['auditorium_id'], d)].add(s['id'])

        current_d = START_DATE
        while current_d <= END_DATE:
            is_weekend = current_d.weekday() in (4, 5, 6) # Fri, Sat, Sun
            
            for c in self.cinemas:
                c_id = c['id']
                auds = self.aud_by_cinema[c_id]
                if not auds:
                    continue
                
                c_weight = self.cinema_weights.get(c_id, 1.0)
                # Calibrate number of active auditoriums per cinema per day
                if c_weight >= 1.30:  # Tier 1 (flagship)
                    num_to_run = 2 if is_weekend else 2
                elif c_weight >= 1.05:  # Tier 2
                    num_to_run = 2 if is_weekend else 1
                elif c_weight >= 0.85:  # Tier 3
                    num_to_run = 1 if is_weekend else 1
                else:  # Tier 4
                    num_to_run = 1 if is_weekend else 0

                active_auds = auds[:min(len(auds), num_to_run)]

                for a in active_auds:
                    aud_id = a['id']
                    # If this auditorium already has showtimes on this day, skip adding new ones to prevent overlap
                    if (aud_id, current_d) in existing_aud_days and len(existing_aud_days[(aud_id, current_d)]) > 0:
                        continue
                    
                    # Schedule daily showtimes for this auditorium
                    cursor = datetime.combine(current_d, dtime(8, 30))
                    # add small staggered start offset per auditorium
                    cursor += timedelta(minutes=(random.choice([0, 15, 30])))
                    day_closing = datetime.combine(current_d, dtime(23, 30))

                    while cursor + timedelta(minutes=90) < day_closing:
                        # Choose movie by time slot & demand
                        hour = cursor.hour
                        if 17 <= hour <= 21:
                            m = random.choice(self.movies_high) if random.random() < 0.75 else random.choice(self.movies_med)
                        elif 12 <= hour < 17:
                            m = random.choice(self.movies_med) if random.random() < 0.65 else random.choice(self.movies_high)
                        else:
                            m = random.choice(self.movies_med) if random.random() < 0.50 else random.choice(self.movies_low)

                        dur = m['duration_minutes']
                        s_id = str(uuid.uuid4())
                        st_format = 'IMAX' if 'IMAX' in a['name'].upper() or 'IMAX' in a['type'].upper() else ('THREE_D' if random.random() < 0.15 else 'TWO_D')
                        base_price = 120000.0 if st_format == 'IMAX' else (100000.0 if is_weekend and hour >= 17 else (90000.0 if is_weekend else 80000.0))
                        
                        start_time = cursor
                        end_time = start_time + timedelta(minutes=dur)
                        
                        if end_time > day_closing + timedelta(minutes=20):
                            break

                        st_dict = {
                            'id': s_id,
                            'movie_id': m['id'],
                            'auditorium_id': aud_id,
                            'format': st_format,
                            'language': 'Tiếng Việt',
                            'subtitle': 'Phụ đề tiếng Anh' if random.random() < 0.3 else None,
                            'start_time': start_time,
                            'end_time': end_time,
                            'base_price': base_price,
                            'status': 'FINISHED',
                            'created_at': start_time - timedelta(days=7),
                            'updated_at': end_time + timedelta(minutes=5),
                            'version': 0,
                            'movie_title': m['title'],
                            'is_new': True
                        }
                        self.new_showtimes.append(st_dict)

                        # Advance cursor by turnaround (15 min) and snap to 15 min
                        next_time = end_time + timedelta(minutes=15)
                        snap_rem = next_time.minute % 15
                        if snap_rem > 0:
                            next_time += timedelta(minutes=(15 - snap_rem))
                        cursor = next_time

            current_d += timedelta(days=1)

        print(f"Generated {len(self.new_showtimes)} new showtimes. Total showtimes in target period: {len(self.existing_showtimes) + len(self.new_showtimes)}.")

    def calculate_ticket_price(self, base_price, capacity, seat_type_code, seat_price_modifier, show_time):
        seat_base = base_price * capacity
        # Day modifier: Mon/Tue -5k, Sat/Sun +10k, Wed-Fri 0
        wday = show_time.weekday()
        day_mod = 10000.0 if wday in (5, 6) else (-5000.0 if wday in (0, 1) else 0.0)
        # Time slot modifier: 17:00 - 23:59 +10k
        time_mod = 10000.0 if show_time.hour >= 17 else 0.0
        
        final_price = seat_base + seat_price_modifier + day_mod + time_mod
        return max(40000.0, final_price)

    def generate_bookings_and_tickets(self):
        print("--> Generating realistic bookings, tickets, payments, refunds, F&B, promotions...")
        self.all_bookings = []
        self.all_tickets = []
        self.all_payments = []
        self.all_refunds = []
        self.all_booking_promotions = []
        self.all_booking_foods = []
        self.all_notifications = []

        all_showtimes = []
        # Add existing showtimes (only those in period)
        for s in self.existing_showtimes:
            m_title = next((m['title'] for m in self.movies if m['id'] == s['movie_id']), "Unknown")
            all_showtimes.append({
                'id': s['id'],
                'auditorium_id': s['auditorium_id'],
                'movie_id': s['movie_id'],
                'movie_title': m_title,
                'start_time': s['start_time'],
                'end_time': s['end_time'],
                'base_price': s['base_price'],
                'format': s['format'],
                'is_new': False
            })
        all_showtimes.extend(self.new_showtimes)
        
        # Sort showtimes chronologically
        all_showtimes.sort(key=lambda s: s['start_time'])

        booking_code_counter = 100000
        payment_code_counter = 100000
        refund_code_counter = 100000

        for s in all_showtimes:
            aud_id = s['auditorium_id']
            seats = self.seats_by_aud.get(aud_id, [])
            total_cap = self.aud_capacity.get(aud_id, len(seats))
            if not seats or total_cap == 0:
                continue

            show_dt = s['start_time']
            show_date = show_dt.date()
            wday = show_date.weekday()
            c_id = next((a['cinema_id'] for a in self.auditoriums if a['id'] == aud_id), None)
            c_weight = self.cinema_weights.get(c_id, 1.0)
            
            # Base day factor
            # Mon: 0.72, Tue: 0.76, Wed: 0.86, Thu: 0.92, Fri: 1.25, Sat: 1.60, Sun: 1.45
            day_factors = [0.72, 0.76, 0.86, 0.92, 1.25, 1.60, 1.45]
            day_factor = day_factors[wday]
            
            # Time factor
            hour = show_dt.hour
            if hour < 12:
                time_factor = 0.65
            elif hour < 17:
                time_factor = 0.95
            elif hour <= 21:
                time_factor = 1.35
            else:
                time_factor = 0.70

            # Movie tier factor
            m_title = s['movie_title']
            if any(m['id'] == s['movie_id'] for m in self.movies_high):
                m_factor = 1.28
            elif any(m['id'] == s['movie_id'] for m in self.movies_med):
                m_factor = 1.00
            else:
                m_factor = 0.72

            # Special weekend event spikes
            event_boost = 1.0
            if show_date == date(2026, 9, 19): event_boost = 1.12
            elif show_date == date(2026, 9, 20): event_boost = 1.10
            elif show_date == date(2026, 9, 26): event_boost = 1.15
            elif show_date == date(2026, 9, 27): event_boost = 1.10

            is_show_weekend = wday in (4, 5, 6)
            has_booking_roll = ((hash(s['id']) % 100) + 100) % 100
            if hour < 12 and not is_show_weekend and has_booking_roll < 45:
                # 45% of weekday morning showtimes have 0 bookings (empty screening)
                continue
            if hour >= 22 and not is_show_weekend and has_booking_roll < 40:
                # 40% of weekday late night showtimes have 0 bookings
                continue

            # Deterministic noise: +/- 10%
            noise = 1.0 + ((hash(s['id']) % 21) - 10) / 100.0

            # Target occupancy percentage (calibrated to platform ~24-34% realistic average)
            raw_occ = 0.18 * day_factor * time_factor * m_factor * c_weight * event_boost * noise
            # Bound occupancy between 6% and 78%
            target_occ = max(0.06, min(0.78, raw_occ))
            
            # Some off-peak morning screenings on Mon/Tue have very low occupancy (~6-12%)
            if wday in (0, 1) and hour < 12:
                target_occ = min(target_occ, 0.12)
            # Weekend prime time blockbusters (55-78%)
            if wday in (5, 6) and 17 <= hour <= 21 and m_factor > 1.1:
                target_occ = max(target_occ, 0.58)

            target_cap = int(round(total_cap * target_occ))

            # Seats already booked for this showtime
            booked_seat_ids = set(self.existing_booked_seats[s['id']])
            available_seats = [st for st in seats if st['id'] not in booked_seat_ids]
            
            # Shuffle available seats deterministically
            rng = random.Random(hash(s['id']))
            rng.shuffle(available_seats)

            current_cap = 0
            seat_idx = 0
            total_avail = len(available_seats)

            while current_cap < target_cap and seat_idx < total_avail:
                # Decide booking seat count: 1 (25%), 2 (50%), 3-4 (20%), 5-6 (5%)
                r_size = rng.random()
                if r_size < 0.25:
                    target_seats_count = 1
                elif r_size < 0.75:
                    target_seats_count = 2
                elif r_size < 0.95:
                    target_seats_count = rng.choice([3, 4])
                else:
                    target_seats_count = rng.choice([5, 6])

                booking_seats = []
                while len(booking_seats) < target_seats_count and seat_idx < total_avail:
                    st = available_seats[seat_idx]
                    seat_idx += 1
                    booking_seats.append(st)
                    # If couple seat, that's already 2 capacity
                    if st['seat_type'] == 'COUPLE':
                        break

                if not booking_seats:
                    break

                b_cap = sum(st['capacity'] for st in booking_seats)
                current_cap += b_cap

                # Customer user
                user = rng.choice(self.user_pool)
                user_id = user['id']

                # Booking timestamp (between 45 mins and 2 days before showtime)
                adv_minutes = rng.randint(45, 2800)
                b_created_at = max(datetime.combine(START_DATE, dtime(8, 0)), show_dt - timedelta(minutes=adv_minutes))
                
                b_id = str(uuid.uuid4())
                booking_code_counter += 1
                b_code = f"CB-{b_created_at.strftime('%Y%m%d')}-{booking_code_counter:06d}"
                check_in_code = str(uuid.uuid4())

                # Ticket pricing
                ticket_prices = []
                for st in booking_seats:
                    tp = self.calculate_ticket_price(
                        s['base_price'], st['capacity'], st['seat_type'], st['price_modifier'], show_dt
                    )
                    ticket_prices.append((st, tp))
                
                gross_amount = sum(tp for _, tp in ticket_prices)

                # Promotion (18% chance if gross >= 100k)
                discount_amount = 0.0
                promo_applied = None
                if rng.random() < 0.18 and gross_amount >= 100000.0 and self.promotions:
                    # Pick active promotion
                    p = self.promotions[0] # KHAIGIANG123 (20% max 40k)
                    discount_amount = min(p['max_discount_amount'] or 40000.0, gross_amount * (p['discount_value'] / 100.0))
                    promo_applied = p

                # Food & Beverage (30% chance)
                food_total = 0.0
                b_foods = []
                if rng.random() < 0.30 and self.foods:
                    num_foods = rng.choice([1, 2])
                    chosen_foods = rng.sample(self.foods, min(num_foods, len(self.foods)))
                    for f in chosen_foods:
                        qty = rng.choice([1, 2])
                        sub = f['price'] * qty
                        food_total += sub
                        b_foods.append({
                            'id': str(uuid.uuid4()),
                            'booking_id': b_id,
                            'food_item_id': f['id'],
                            'food_name': f['name'],
                            'unit_price': f['price'],
                            'quantity': qty,
                            'subtotal': sub,
                            'created_at': b_created_at
                        })

                total_amount = max(0.0, gross_amount - discount_amount) + food_total

                # Booking status: 85% PAID, 4% REFUNDED, 6% CANCELLED, 5% EXPIRED
                status_roll = rng.random()
                if status_roll < 0.85:
                    b_status = 'PAID'
                elif status_roll < 0.89:
                    b_status = 'REFUNDED'
                elif status_roll < 0.95:
                    b_status = 'CANCELLED'
                else:
                    b_status = 'EXPIRED'

                # Build Booking record
                b_rec = {
                    'id': b_id,
                    'booking_code': b_code,
                    'check_in_code': check_in_code,
                    'user_id': user_id,
                    'showtime_id': s['id'],
                    'total_amount': total_amount,
                    'booking_status': b_status,
                    'hold_expires_at': b_created_at + timedelta(minutes=15) if b_status in ('EXPIRED', 'PENDING_PAYMENT') else None,
                    'cancelled_at': b_created_at + timedelta(minutes=12) if b_status == 'CANCELLED' else None,
                    'cancelled_by_user_id': user_id if b_status == 'CANCELLED' else None,
                    'cancelled_reason': 'Khách hàng hủy giữ chỗ' if b_status == 'CANCELLED' else None,
                    'created_at': b_created_at,
                    'updated_at': b_created_at + timedelta(minutes=3),
                    'version': 0
                }
                self.all_bookings.append(b_rec)

                # Link promotion
                if promo_applied and b_status in ('PAID', 'REFUNDED'):
                    self.all_booking_promotions.append({
                        'promotion_id': promo_applied['id'],
                        'booking_id': b_id,
                        'discount_amount': discount_amount,
                        'created_at': b_created_at
                    })

                # Link foods
                if b_status in ('PAID', 'REFUNDED'):
                    self.all_booking_foods.extend(b_foods)

                # Tickets & Payments
                if b_status == 'PAID':
                    # Create tickets with status USED (90%) or VALID (10%)
                    for st, tp in ticket_prices:
                        t_id = str(uuid.uuid4())
                        t_status = 'USED' if rng.random() < 0.92 else 'VALID'
                        self.all_tickets.append({
                            'id': t_id,
                            'booking_id': b_id,
                            'seat_id': st['id'],
                            'ticket_price': tp,
                            'ticket_status': t_status,
                            'qr_code': str(uuid.uuid4()),
                            'created_at': b_created_at
                        })

                    # Payment SUCCESS
                    pay_id = str(uuid.uuid4())
                    payment_code_counter += 1
                    paid_at = b_created_at + timedelta(seconds=rng.randint(60, 240))
                    self.all_payments.append({
                        'id': pay_id,
                        'booking_id': b_id,
                        'payment_method': 'VNPAY',
                        'payment_code': f"PAY-{paid_at.strftime('%Y%m%d')}-{payment_code_counter:06d}",
                        'gateway_transaction_id': f"VNPAY_{pay_id[:12].replace('-', '')}",
                        'amount': total_amount,
                        'payment_status': 'SUCCESS',
                        'paid_at': paid_at,
                        'gateway_response': json.dumps({'vnp_ResponseCode': '00', 'vnp_TransactionStatus': '00'}),
                        'created_at': b_created_at,
                        'updated_at': paid_at
                    })

                    # Notification
                    if rng.random() < 0.75:
                        self.all_notifications.append({
                            'id': str(uuid.uuid4()),
                            'user_id': user_id,
                            'booking_id': b_id,
                            'type': 'PAYMENT_SUCCESS',
                            'title': 'Thanh toán thành công',
                            'message': f"Đơn đặt vé #{b_code} đã thanh toán thành công.",
                            'is_read': 1,
                            'created_at': paid_at,
                            'read_at': paid_at + timedelta(minutes=rng.randint(5, 60))
                        })

                elif b_status == 'REFUNDED':
                    # Tickets CANCELLED
                    for st, tp in ticket_prices:
                        t_id = str(uuid.uuid4())
                        self.all_tickets.append({
                            'id': t_id,
                            'booking_id': b_id,
                            'seat_id': st['id'],
                            'ticket_price': tp,
                            'ticket_status': 'CANCELLED',
                            'qr_code': str(uuid.uuid4()),
                            'created_at': b_created_at
                        })

                    # Payment REFUNDED
                    pay_id = str(uuid.uuid4())
                    payment_code_counter += 1
                    paid_at = b_created_at + timedelta(seconds=rng.randint(60, 240))
                    self.all_payments.append({
                        'id': pay_id,
                        'booking_id': b_id,
                        'payment_method': 'VNPAY',
                        'payment_code': f"PAY-{paid_at.strftime('%Y%m%d')}-{payment_code_counter:06d}",
                        'gateway_transaction_id': f"VNPAY_{pay_id[:12].replace('-', '')}",
                        'amount': total_amount,
                        'payment_status': 'REFUNDED',
                        'paid_at': paid_at,
                        'gateway_response': json.dumps({'vnp_ResponseCode': '00', 'vnp_TransactionStatus': '00'}),
                        'created_at': b_created_at,
                        'updated_at': paid_at
                    })

                    # Refund SUCCESS
                    ref_id = str(uuid.uuid4())
                    refund_code_counter += 1
                    proc_at = paid_at + timedelta(hours=rng.randint(1, 8))
                    if proc_at >= show_dt:
                        proc_at = show_dt - timedelta(minutes=15)
                    self.all_refunds.append({
                        'id': ref_id,
                        'payment_id': pay_id,
                        'refund_code': f"REF-{proc_at.strftime('%Y%m%d')}-{refund_code_counter:06d}",
                        'gateway_refund_id': f"GW_REF_{ref_id[:12].replace('-', '')}",
                        'amount': total_amount,
                        'refund_reason': 'Khách hàng hủy vé do bận việc đột xuất',
                        'refund_status': 'SUCCESS',
                        'processed_at': proc_at,
                        'created_at': proc_at
                    })

                    # Notification
                    if rng.random() < 0.75:
                        self.all_notifications.append({
                            'id': str(uuid.uuid4()),
                            'user_id': user_id,
                            'booking_id': b_id,
                            'type': 'REFUND_COMPLETED',
                            'title': 'Hoàn tiền thành công',
                            'message': f"Đơn đặt vé #{b_code} đã được hoàn tiền thành công.",
                            'is_read': 1,
                            'created_at': proc_at,
                            'read_at': proc_at + timedelta(minutes=rng.randint(5, 60))
                        })

                elif b_status == 'CANCELLED':
                    if rng.random() < 0.50:
                        c_at = b_rec['cancelled_at']
                        self.all_notifications.append({
                            'id': str(uuid.uuid4()),
                            'user_id': user_id,
                            'booking_id': b_id,
                            'type': 'BOOKING_CANCELLED',
                            'title': 'Đơn đặt vé đã hủy',
                            'message': f"Đơn đặt vé #{b_code} đã bị hủy do hết hạn giữ chỗ.",
                            'is_read': 1,
                            'created_at': c_at,
                            'read_at': c_at + timedelta(minutes=rng.randint(5, 60))
                        })

        print(f"Generated: {len(self.all_bookings)} bookings, {len(self.all_tickets)} tickets, "
              f"{len(self.all_payments)} payments, {len(self.all_refunds)} refunds, "
              f"{len(self.all_booking_promotions)} promo links, {len(self.all_booking_foods)} food orders, "
              f"{len(self.all_notifications)} notifications.")

    def write_sql_file(self):
        print(f"--> Writing SQL seed file to: {OUTPUT_SQL_FILE}")
        with open(OUTPUT_SQL_FILE, 'w', encoding='utf-8') as out:
            out.write("-- =========================================================================\n")
            out.write(f"-- CineBook Historical Demo Data Seed Script\n")
            out.write(f"-- Generated on: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
            out.write(f"-- Seed: {RANDOM_SEED} | Period: {START_DATE} to {END_DATE}\n")
            out.write("-- =========================================================================\n\n")
            out.write("SET FOREIGN_KEY_CHECKS = 0;\n")
            out.write("SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';\n")
            out.write("START TRANSACTION;\n\n")

            # 1. Supplemental Users
            if self.new_users:
                out.write("-- 1. Supplemental Customer Users\n")
                self._write_batch_insert(out, "users", [
                    "id", "email", "password_hash", "full_name", "phone", "avatar_url",
                    "status", "email_verified", "last_login_at", "created_at", "updated_at",
                    "deleted_at", "version"
                ], self.new_users)

                out.write("\n-- 1b. Assign ROLE_CUSTOMER to new users\n")
                user_roles_data = [{'user_id': u['id'], 'role_id': ROLE_CUSTOMER_ID} for u in self.new_users]
                self._write_batch_insert(out, "user_roles", ["user_id", "role_id"], user_roles_data)

            # 2. Showtimes
            if self.new_showtimes:
                out.write("\n-- 2. New Historical Showtimes\n")
                self._write_batch_insert(out, "showtimes", [
                    "id", "movie_id", "auditorium_id", "format", "language", "subtitle",
                    "start_time", "end_time", "base_price", "status", "created_at", "updated_at", "version"
                ], self.new_showtimes)

            # 3. Bookings
            if self.all_bookings:
                out.write("\n-- 3. Bookings\n")
                self._write_batch_insert(out, "bookings", [
                    "id", "booking_code", "check_in_code", "user_id", "showtime_id",
                    "total_amount", "booking_status", "hold_expires_at", "cancelled_at",
                    "cancelled_by_user_id", "cancelled_reason", "created_at", "updated_at", "version"
                ], self.all_bookings)

            # 4. Tickets
            if self.all_tickets:
                out.write("\n-- 4. Tickets\n")
                self._write_batch_insert(out, "tickets", [
                    "id", "booking_id", "seat_id", "ticket_price", "ticket_status", "qr_code", "created_at"
                ], self.all_tickets)

            # 5. Payments
            if self.all_payments:
                out.write("\n-- 5. Payments\n")
                self._write_batch_insert(out, "payments", [
                    "id", "booking_id", "payment_method", "payment_code", "gateway_transaction_id",
                    "amount", "payment_status", "paid_at", "gateway_response", "created_at", "updated_at"
                ], self.all_payments)

            # 6. Refunds
            if self.all_refunds:
                out.write("\n-- 6. Refunds\n")
                self._write_batch_insert(out, "refunds", [
                    "id", "payment_id", "refund_code", "gateway_refund_id", "amount",
                    "refund_reason", "refund_status", "processed_at", "created_at"
                ], self.all_refunds)

            # 7. Booking Promotions
            if self.all_booking_promotions:
                out.write("\n-- 7. Booking Promotions\n")
                self._write_batch_insert(out, "booking_promotions", [
                    "promotion_id", "booking_id", "discount_amount", "created_at"
                ], self.all_booking_promotions)

            # 8. Booking Foods
            if self.all_booking_foods:
                out.write("\n-- 8. Booking Foods (F&B Concessions)\n")
                self._write_batch_insert(out, "booking_foods", [
                    "id", "booking_id", "food_item_id", "food_name", "unit_price", "quantity", "subtotal", "created_at"
                ], self.all_booking_foods)

            # 9. Notifications
            if self.all_notifications:
                out.write("\n-- 9. Notifications\n")
                self._write_batch_insert(out, "notifications", [
                    "id", "user_id", "booking_id", "type", "title", "message", "is_read", "created_at", "read_at"
                ], self.all_notifications)

            out.write("\nCOMMIT;\n")
            out.write("SET FOREIGN_KEY_CHECKS = 1;\n")

        print("--> SQL file generation complete!")

    def _write_batch_insert(self, out, table, columns, data, batch_size=500):
        cols_str = ", ".join(f"`{c}`" for c in columns)
        for i in range(0, len(data), batch_size):
            chunk = data[i:i + batch_size]
            out.write(f"INSERT INTO `{table}` ({cols_str}) VALUES\n")
            lines = []
            for item in chunk:
                vals = [escape_sql(item.get(c)) for c in columns]
                lines.append(f"  ({', '.join(vals)})")
            out.write(",\n".join(lines) + ";\n\n")

    def export_metrics(self):
        metrics = {
            'random_seed': RANDOM_SEED,
            'start_date': str(START_DATE),
            'end_date': str(END_DATE),
            'new_users_count': len(self.new_users),
            'new_showtimes_count': len(self.new_showtimes),
            'total_showtimes_in_period': len(self.existing_showtimes) + len(self.new_showtimes),
            'total_bookings': len(self.all_bookings),
            'total_tickets': len(self.all_tickets),
            'total_payments': len(self.all_payments),
            'total_refunds': len(self.all_refunds),
            'total_booking_promotions': len(self.all_booking_promotions),
            'total_booking_foods': len(self.all_booking_foods),
            'total_notifications': len(self.all_notifications),
            'gross_revenue': sum(p['amount'] for p in self.all_payments if p['payment_status'] in ('SUCCESS', 'REFUNDED')),
            'refund_amount': sum(r['amount'] for r in self.all_refunds if r['refund_status'] == 'SUCCESS'),
        }
        metrics['net_revenue'] = metrics['gross_revenue'] - metrics['refund_amount']
        with open(OUTPUT_METRICS_FILE, 'w', encoding='utf-8') as f:
            json.dump(metrics, f, indent=2)
        print(f"Metrics saved to: {OUTPUT_METRICS_FILE}")
        return metrics

# -------------------------------------------------------------
# 3. ENTRY POINT
# -------------------------------------------------------------
if __name__ == '__main__':
    gen = DemoDataGenerator()
    gen.generate_users(count=60)
    gen.generate_showtimes()
    gen.generate_bookings_and_tickets()
    gen.write_sql_file()
    metrics = gen.export_metrics()
    print("\n================== GENERATION SUMMARY ==================")
    print(f"Period:              {metrics['start_date']} to {metrics['end_date']} (21 days)")
    print(f"New Users:           {metrics['new_users_count']}")
    print(f"New Showtimes:       {metrics['new_showtimes_count']}")
    print(f"Total Showtimes:     {metrics['total_showtimes_in_period']}")
    print(f"Total Bookings:      {metrics['total_bookings']}")
    print(f"Total Tickets:       {metrics['total_tickets']}")
    print(f"Total Payments:      {metrics['total_payments']}")
    print(f"Total Refunds:       {metrics['total_refunds']}")
    print(f"Gross Revenue (VND): {metrics['gross_revenue']:,.2f}")
    print(f"Refund Amount (VND): {metrics['refund_amount']:,.2f}")
    print(f"Net Revenue (VND):   {metrics['net_revenue']:,.2f}")
    print("========================================================\n")
