import asyncio
import aiohttp
import time
import json
import statistics
import openpyxl
from collections import defaultdict
from pathlib import Path

BASE_URL = "http://localhost:5000"
DURATION = 60
CONCURRENCY = 100

# Get tokens
try:
    with open("input.json") as f:
        config = json.load(f)
        admin_token = config.get("admin", "")
        driver_token = config.get("driver", "")
        user_token = config.get("user", "")
        driver_id = config.get("testDriverId", "mock-driver-1")
        user_id = config.get("testUserId", "mock-user-1")
except:
    print("Warning: input.json not found, using empty tokens")
    admin_token = driver_token = user_token = ""
    driver_id = user_id = ""

ENDPOINTS = []

# Generate 75 test cases for Get Hospitals
for i in range(1, 76):
    ENDPOINTS.append({
        "method": "GET",
        "path": f"/api/hospitals?limit={i}",
        "headers": {"Authorization": f"Bearer {user_token}", "x-load-test-bypass": "true"},
        "name": f"TC_{i:03d}: Get Hospitals (Limit {i})"
    })

# Generate 75 test cases for Admin Available Ambulances
for i in range(1, 76):
    ENDPOINTS.append({
        "method": "GET",
        "path": f"/api/admin/ambulances/available?cache_bust={i}",
        "headers": {"Authorization": f"Bearer {admin_token}", "x-load-test-bypass": "true"},
        "name": f"TC_{i+75:03d}: Admin Get Available Ambulances (Bust {i})"
    })

# Generate 75 test cases for Driver Location Update
for i in range(1, 76):
    lat = 17.3850 + (i * 0.0001)
    lng = 78.4867 - (i * 0.0001)
    ENDPOINTS.append({
        "method": "POST",
        "path": f"/api/driver/ambulances/{driver_id}/location",
        "headers": {"Authorization": f"Bearer {driver_token}", "x-load-test-bypass": "true", "Content-Type": "application/json"},
        "json": {"lat": lat, "lng": lng, "speed": i},
        "name": f"TC_{i+150:03d}: Driver Location Update (Seq {i})"
    })

# Generate 75 test cases for User Profile
for i in range(1, 76):
    ENDPOINTS.append({
        "method": "GET",
        "path": f"/api/auth/profile/{user_id}?req_id={i}",
        "headers": {"Authorization": f"Bearer {user_token}", "x-load-test-bypass": "true"},
        "name": f"TC_{i+225:03d}: Get User Profile (Req {i})"
    })

metrics = defaultdict(list)
status_codes = defaultdict(lambda: defaultdict(int))
start_time = 0
running = True

async def worker(session, worker_id):
    global running
    import random
    
    while running:
        ep = random.choice(ENDPOINTS)
        req_start = time.perf_counter()
        
        try:
            async with session.request(
                method=ep["method"],
                url=BASE_URL + ep["path"],
                headers=ep["headers"],
                json=ep.get("json")
            ) as resp:
                await resp.read()
                status = resp.status
        except Exception as e:
            status = 0
            
        latency = (time.perf_counter() - req_start) * 1000  # in ms
        metrics[ep["name"]].append(latency)
        status_codes[ep["name"]][status] += 1

async def main():
    global start_time, running
    
    print(f"Starting Load Test: {CONCURRENCY} users for {DURATION} seconds...")
    print(f"Targeting: {BASE_URL}")
    
    connector = aiohttp.TCPConnector(limit=CONCURRENCY)
    async with aiohttp.ClientSession(connector=connector) as session:
        start_time = time.time()
        
        # Start workers
        tasks = []
        for i in range(CONCURRENCY):
            tasks.append(asyncio.create_task(worker(session, i)))
            
        # Wait for duration
        await asyncio.sleep(DURATION)
        running = False
        
        # Wait for workers to finish their last request
        await asyncio.gather(*tasks)

    end_time = time.time()
    actual_duration = end_time - start_time
    
    total_requests = sum(len(latencies) for latencies in metrics.values())
    overall_rps = total_requests / actual_duration
    
    print("\n" + "="*50)
    print("LOAD TEST RESULTS")
    print("="*50)
    print(f"Duration:        {actual_duration:.2f} seconds")
    print(f"Concurrent Users:{CONCURRENCY}")
    print(f"Total Requests:  {total_requests}")
    print(f"Overall RPS:     {overall_rps:.2f} req/sec")
    print("="*50)
    
    # Generate Excel
    wb = openpyxl.Workbook()
    ws = wb.active
    ws.title = "Load Test Summary"
    
    ws.append(["Endpoint", "Total Requests", "Status Codes", "Min (ms)", "Max (ms)", "Avg (ms)", "RPS"])
    
    for name, latencies in metrics.items():
        count = len(latencies)
        if count == 0:
            continue
            
        min_lt = min(latencies)
        max_lt = max(latencies)
        avg_lt = statistics.mean(latencies)
        rps = count / actual_duration
        codes = dict(status_codes[name])
        codes_str = ", ".join(f"{k}: {v}" for k, v in codes.items())
        
        print(f"\n[{name}]")
        print(f"  Requests: {count}")
        print(f"  Status:   {codes_str}")
        print(f"  Latency:  Min={min_lt:.2f}ms, Max={max_lt:.2f}ms, Avg={avg_lt:.2f}ms")
        print(f"  RPS:      {rps:.2f} req/sec")
        
        ws.append([
            name,
            count,
            codes_str,
            round(min_lt, 2),
            round(max_lt, 2),
            round(avg_lt, 2),
            round(rps, 2)
        ])
        
    out_file = "automated_test/Load_Test_Report_300.xlsx"
    wb.save(out_file)
    print(f"\nDetailed report saved to: {out_file}")

if __name__ == "__main__":
    # Prevent ProactorEventLoop error on Windows shutdown
    import sys
    if sys.version_info[0] == 3 and sys.version_info[1] >= 8 and sys.platform.startswith('win'):
        asyncio.set_event_loop_policy(asyncio.WindowsSelectorEventLoopPolicy())
        
    asyncio.run(main())
