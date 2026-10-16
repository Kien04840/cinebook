import subprocess
import csv
import io
import json

def run_query(sql):
    res = subprocess.run(
        ['mysql', '-u', 'root', '--default-character-set=utf8mb4', '--batch', '--raw', '-e', sql, 'cinebook'],
        capture_output=True,
        text=True,
        encoding='utf-8'
    )
    if res.returncode != 0:
        raise RuntimeError(f"SQL Error: {res.stderr}\nQuery: {sql}")
    reader = csv.DictReader(io.StringIO(res.stdout), delimiter='\t')
    return list(reader)

if __name__ == '__main__':
    cinemas = run_query("SELECT id, name, city FROM cinemas WHERE status='ACTIVE' AND deleted_at IS NULL;")
    print(f"Loaded {len(cinemas)} active cinemas.")
    movies = run_query("SELECT id, title, duration_minutes FROM movies WHERE status='NOW_SHOWING' AND deleted_at IS NULL AND duration_minutes >= 60;")
    print(f"Loaded {len(movies)} active movies.")
    seats = run_query("SELECT s.id, s.auditorium_id, st.code as seat_type, st.capacity, st.price_modifier FROM seats s JOIN seat_types st ON s.seat_type_id=st.id WHERE s.status='ACTIVE' AND st.status='ACTIVE';")
    print(f"Loaded {len(seats)} active seats.")

