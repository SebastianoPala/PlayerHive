import json
import random
import csv
import sys
import hashlib
import uuid
from datetime import datetime, timedelta
import bcrypt
from faker import Faker
from neo4j import GraphDatabase
from pymongo import MongoClient
from bson import ObjectId

# --- CONFIGURATION ---
NEO4J_URI = "bolt://localhost:7687"
NEO4J_USER = "neo4j"
NEO4J_PASSWORD = "00000000" 
MONGO_URI = "mongodb://localhost:27017/"
MONGO_DB_NAME = "PlayerHive"

NUM_USERS = 15000            # Number of users to generate
NUM_ROUNDS = 12              # The lower the value, the faster the population script. this value has to
                             # match the value at line 94 in SecuritySpringBoot.java
MAX_USER_REVIEWS = 40
MAX_GAMES = 40
MAX_FRIENDS = 20

STATIC_USER = "aleach"

fake = Faker()

admin_user = {
    "_id": {"$oid": uuid.uuid4().hex[:24]},
    "username": "admin",
    "password": bcrypt.hashpw("admin".encode('utf-8'), bcrypt.gensalt(rounds=NUM_ROUNDS)).decode('utf-8'),
    "email": "admin@hotmail.com",
    "birthdate": datetime(1776, 12, 4), 
    "registrationDate": datetime.now() - timedelta(days=5 * 365),
    "role": "ADMIN",
    "friendRequests": [],
    "requestsNum": 0,
    "friends": 0,
    "numGames": 0,
    "hoursPlayed": 0.0,
    "pfpURL": f"/Playerhive/pfp/{uuid.uuid4().hex}",
    "reviewIds": []
}

default_user = {
    "_id": {"$oid": uuid.uuid4().hex[:24]},
    "username": STATIC_USER,
    "password": bcrypt.hashpw(STATIC_USER.encode('utf-8'), bcrypt.gensalt(rounds=NUM_ROUNDS)).decode('utf-8'),
    "email": STATIC_USER + "@hotmail.com",
    "birthdate": datetime(1776, 12, 4), 
    "registrationDate": datetime.now() - timedelta(days=5 * 365),
    "role": "USER",
    "friendRequests": [],
    "requestsNum": 0,
    "friends": 0,
    "numGames": 0,
    "hoursPlayed": 0.0,
    "pfpURL": f"/Playerhive/pfp/{uuid.uuid4().hex}",
    "reviewIds": []
}

# remove CSV field size limits to prevent errors with large text blocks
maxInt = sys.maxsize
while True:
    try:
        csv.field_size_limit(maxInt)
        break
    except OverflowError:
        maxInt = int(maxInt/10)

# --- UTILITY FUNCTIONS ---
def generate_oid():
    """Simulates a MongoDB ObjectId (24 hex characters)"""
    return uuid.uuid4().hex[:24]

def generate_random_date():
    """Generates a random datetime object between Jan 1, 2023 and today"""
    start_date = datetime(2023, 1, 1)
    end_date = datetime.now()
    delta = end_date - start_date
    random_seconds = random.randrange(int(delta.total_seconds()))
    return start_date + timedelta(seconds=random_seconds)

def generate_registration_date():
    """Generates a random datetime object between 5 years ago and today"""
    end_date = datetime.now()
    start_date = end_date - timedelta(days=5 * 365)
    delta = end_date - start_date
    random_seconds = random.randrange(int(delta.total_seconds()))
    return start_date + timedelta(seconds=random_seconds)

def generate_recent_timestamp():
    """Generates a datetime object not older than 1 month"""
    end_date = datetime.now()
    start_date = end_date - timedelta(days=30)
    return fake.date_time_between(start_date=start_date, end_date=end_date)

def format_to_datetime(date_raw):
    """Parses various date formats and converts them to a Python datetime object (at midnight)."""
    if not date_raw:
        return None
    date_str = str(date_raw).strip()

    formats = [
        "%Y-%m-%d", "%b %d, %Y", "%d %b, %Y", "%B %d, %Y", "%d %B, %Y", 
        "%Y/%m/%d", "%m/%d/%Y", "%d/%m/%Y"
    ]
    for fmt in formats:
        try:
            return datetime.strptime(date_str, fmt)
        except ValueError:
            continue

    if len(date_str) == 4 and date_str.isdigit():
        return datetime(int(date_str), 1, 1)
    return None 

# --- DATABASE FUNCTIONS ---
def check_neo4j_connection():
    """Verifies Neo4j credentials before starting the heavy processing."""
    print("Checking Neo4j connection...")
    try:
        driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASSWORD))
        driver.verify_connectivity()
        driver.close()
        print(" -> Neo4j authentication successful!\n")
    except Exception as e:
        print(f" -> ERROR: Failed to connect to Neo4j. Check credentials or ensure the server is running.\n{e}")
        sys.exit(1)

def clear_neo4j(session):
    print("   -> Dropping the existing Neo4j database (DETACH DELETE n)...")
    session.run("MATCH (n) DETACH DELETE n")

def manage_neo4j_indexes(session, action="CREATE"):
    if action == "CREATE":
        print("   -> Creating unique constraints on User.id and Game.id...")
        session.run("CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE")
        session.run("CREATE CONSTRAINT game_id_unique IF NOT EXISTS FOR (g:Game) REQUIRE g.id IS UNIQUE")
        session.run("CALL db.awaitIndexes(300)")
    elif action == "DROP":
        print("   -> Dropping constraints...")
        session.run("DROP CONSTRAINT user_id_unique IF EXISTS")
        session.run("DROP CONSTRAINT game_id_unique IF EXISTS")

def create_mongo_indexes(db):
    """Creates persistent MongoDB indexes for the application's hot read paths.
    Built once, after all bulk inserts, so we don't pay index-maintenance cost per insert."""
    print("   -> Creating MongoDB indexes...")

    db.users.create_index("email", unique=True, name="email_1")
    db.users.create_index("username", unique=True, name="username_1")

    db.games.create_index("name", unique=True ,name="name_1")

    db.games.create_index([("release_date", -1)], name="release_date_-1")

    print("   -> MongoDB indexes created!")

def upload_to_mongodb(games_data, users_data, all_reviews_data):
    """Uploads the finalized in-memory dictionaries directly to MongoDB."""
    print(f"\n7. Connecting to MongoDB ({MONGO_URI})...")
    client = MongoClient(MONGO_URI)
    db = client[MONGO_DB_NAME]
    
    print(f"   -> Dropping existing '{MONGO_DB_NAME}' collections (games, users, reviews)...")
    db.games.drop()
    db.users.drop()
    db.reviews.drop()

    print("   -> Formatting ObjectIds for MongoDB insertion...")
    mongo_games = []
    for g in games_data:
        g_copy = g.copy()
        g_copy["_id"] = ObjectId(g["_id"]["$oid"])
        
        g_copy["allReviews"] = [
            ObjectId(r_id) 
            for r_id in g_copy.get("allReviews", [])
        ]
        
        for rr in g_copy.get("recentReviews", []):
            rr["_id"] = ObjectId(rr["_id"]["$oid"])
            rr["user_id"] = ObjectId(rr["user_id"])
        mongo_games.append(g_copy)
        
    mongo_users = []
    for u in users_data:
        u_copy = u.copy()
        u_copy["_id"] = ObjectId(u["_id"]["$oid"])
        
        for req in u_copy.get("friendRequests", []):
            req["user_id"] = ObjectId(req["user_id"])

        for rev in u_copy.get("reviewIds", []):
            rev["review_id"] = ObjectId(rev["review_id"])
            rev["game_id"] = ObjectId(rev["game_id"])
            
        mongo_users.append(u_copy)
        
    mongo_reviews = []
    for r in all_reviews_data:
        r_copy = r.copy()
        r_copy["_id"] = ObjectId(r["_id"]["$oid"])
        r_copy["game_id"] = ObjectId(r["game_id"])
        r_copy["user_id"] = ObjectId(r["user_id"])
        mongo_reviews.append(r_copy)

    print("   -> Inserting Game documents into MongoDB...")
    db.games.insert_many(mongo_games)
    
    print("   -> Inserting User documents into MongoDB...")
    db.users.insert_many(mongo_users)
    
    if mongo_reviews:
        print("   -> Inserting Review documents into MongoDB...")
        db.reviews.insert_many(mongo_reviews)
        
    print("   -> MongoDB upload complete!")
    create_mongo_indexes(db)
    client.close()


# --- MAIN PIPELINE ---
def main():
    print("=== STARTING MASTER PIPELINE ===\n")
    
    check_neo4j_connection()
    
    print("1. Loading and cleaning Games data (from games.json)...")
    try:
        with open('games.json', 'r', encoding='utf-8') as f:
            original_games = json.load(f)
    except FileNotFoundError:
        print("ERROR: 'games.json' not found in the directory.")
        sys.exit(1)
        
    games_list = []
    seen_names = set()

    for app_id, game_data in original_games.items():
        game_name = game_data.get("name", "").strip()
        if not game_name or game_name.lower() in seen_names:
            continue
        seen_names.add(game_name.lower())

        supported_os = [os for os in ["windows", "mac", "linux"] if game_data.get(os)]
        raw_tags = game_data.get("tags")
        flat_genres = []
        if isinstance(raw_tags, dict): 
            flat_genres = list(raw_tags.keys())
        elif isinstance(raw_tags, list) and len(raw_tags) > 0 and isinstance(raw_tags[0], dict):
            for tag_dict in raw_tags: 
                flat_genres.extend(tag_dict.keys())
        elif isinstance(raw_tags, list): 
            flat_genres = raw_tags
            
        random_price = float(game_data.get("price", 0.0) if game_data.get("price") is not None else 0.0)

        if random_price == 0 or random.random() < 0.70:
            discount = 0
        else:
            discount = random.choice(range(0, 95, 5))
        
        finalPrice = random_price - (random_price * int(discount)/100)

        games_list.append({
            "_id": {"$oid": generate_oid()},
            "name": game_data.get("name", ""),
            "release_date": format_to_datetime(game_data.get("release_date")),
            "price": round(random_price,2),
            "discount": int(discount),
            "finalPrice": round(finalPrice,2),
            "description": game_data.get("detailed_description"),
            "image": game_data.get("header_image"),
            "supportedOS": supported_os,
            "achievements": int(game_data.get("achievements", 0)), 
            "developers": game_data.get("developers", []),
            "publishers": game_data.get("publishers", []),
            "genres": flat_genres,
            "raw_reviews": [] 
        })
    print(f"   -> Successfully loaded and cleaned {len(games_list)} games.")

    print(f"\n2. Generating {NUM_USERS} mock Users in memory...")
    users_list = []
    for i in range(NUM_USERS):
        username = fake.unique.user_name()
        birthdate_raw = fake.date_of_birth(minimum_age=15)
        birthdate_dt = datetime(birthdate_raw.year, birthdate_raw.month, birthdate_raw.day)
        
        users_list.append({
            "_id": {"$oid": generate_oid()},
            "username": username,
            "password": bcrypt.hashpw(username.encode('utf-8'), bcrypt.gensalt(rounds=NUM_ROUNDS)).decode('utf-8'),
            "email": username + "@hotmail.com",
            "birthdate": birthdate_dt, 
            "registrationDate": generate_registration_date(),
            "role": "USER",
            "friendRequests": [],
            "requestsNum": 0,
            "friends": 0,
            "numGames": 0,
            "hoursPlayed": 0.0,
            "pfpURL": f"/Playerhive/pfp/{uuid.uuid4().hex}",
            "reviewIds": []
        })
        if (i + 1) % 1000 == 0:
            print(f"   -> Generated {i + 1}/{NUM_USERS} users...", end='\r')
    print(f"   -> Generated {NUM_USERS}/{NUM_USERS} users...", end='\r')
    print()
    print("Adding the admin account and default account...")
    users_list.append(admin_user)
    users_list.append(default_user)
    print()

    print("\n3. Extracting texts from 'reviews.csv' and generating user reviews...")
    review_texts = []
    try:
        with open('reviews.csv', 'r', encoding='utf-8') as f:
            for row in csv.DictReader(f):
                if 'review_text' in row and row['review_text'].strip():
                    review_texts.append(row['review_text'])
    except FileNotFoundError:
        print("ERROR: 'reviews.csv' not found.")
        sys.exit(1)
        
    all_global_reviews = [] 
        
    for i, user in enumerate(users_list):
        if(user["username"] == STATIC_USER):
            M = MAX_USER_REVIEWS
        else:
            M = random.randint(0, MAX_USER_REVIEWS)
        user_reviews_temp = []
        
        for _ in range(M):
            game = random.choice(games_list)
            review_doc = {
                "_id": {"$oid": generate_oid()},
                "game_id": game["_id"]["$oid"],
                "user_id": user['_id']['$oid'],
                "username": user['username'],
                "pfpURL": user["pfpURL"],
                "game_name": game["name"],
                "game_image": game["image"], 
                "review_text": random.choice(review_texts),
                "score": float(round(random.uniform(1.0, 10.0), 1)),
                "timestamp": generate_random_date() 
            }
            game["raw_reviews"].append(review_doc)
            all_global_reviews.append(review_doc)
            user_reviews_temp.append(review_doc)
            
        user_reviews_temp.sort(key=lambda x: x["timestamp"], reverse=True)
        user["reviewIds"] = [
            {
                "review_id": r["_id"]["$oid"],
                "game_id": r["game_id"]
            }
            for r in user_reviews_temp
        ]
            
        if (i + 1) % 1000 == 0 or (i + 1) == len(users_list):
            print(f"   -> Assigned reviews for {i + 1}/{len(users_list)} users...", end='\r')
    print()
            
    print("   -> Structuring recentReviews and allReviews for MongoDB optimization...")
    for i, game in enumerate(games_list):
        game["raw_reviews"].sort(key=lambda x: x["timestamp"], reverse=True)
        
        if game["raw_reviews"]:
            game["sumScore"] = float(round(sum(r["score"] for r in game["raw_reviews"]), 1))
            game["countScore"] = int(len(game["raw_reviews"]))
        else:
            game["sumScore"] = 0.0
            game["countScore"] = 0
            
        game["allReviews"] = [
            r["_id"]["$oid"]
            for r in game["raw_reviews"]
        ]
        
        recent_25 = game["raw_reviews"][:25]
        
        embedded_recent = []
        for r in recent_25:
            r_copy = r.copy()
            r_copy.pop("game_id", None) 
            embedded_recent.append(r_copy)
            
        game["recentReviews"] = embedded_recent
        del game["raw_reviews"]
            
        if (i + 1) % 1000 == 0 or (i + 1) == len(games_list):
            print(f"   -> Processed structural reviews for {i + 1}/{len(games_list)} games...", end='\r')
    print()

    print("\n4. Generating play history metrics and [:PLAYED] relationships...")
    played_relationships = []
    game_playtime_stats = {g["_id"]["$oid"]: {"total_hours": 0.0, "user_count": 0} for g in games_list}
    
    for i, user in enumerate(users_list):
        if(user["username"] == STATIC_USER):
            M = min(MAX_GAMES, len(games_list))
        else: 
            M = min(random.randint(0, MAX_GAMES), len(games_list))

        selected_games = random.sample(games_list, M)
        total_hours = 0.0
        
        for game in selected_games:
            hours = float(round(random.uniform(0.1, 500.0), 1))
            total_hours += hours
            achievements = int(random.randint(0, game.get("achievements", 0)))
            
            game_id_str = game["_id"]["$oid"]
            played_relationships.append({
                "user_id": user["_id"]["$oid"], 
                "game_id": game_id_str, 
                "hoursPlayed": hours, 
                "achievements": achievements
            })
            
            game_playtime_stats[game_id_str]["total_hours"] += hours
            game_playtime_stats[game_id_str]["user_count"] += 1
            
        user["numGames"] = int(M)
        user["hoursPlayed"] = float(round(total_hours, 1))
        
        if (i + 1) % 1000 == 0 or (i + 1) == len(users_list):
            print(f"   -> Generated play history for {i + 1}/{len(users_list)} users...", end='\r')
    print()

    print("   -> Calculating totalHoursPlayed and numPlayers for games...")
    for i, game in enumerate(games_list):
        stats = game_playtime_stats[game["_id"]["$oid"]]
        if stats["user_count"] > 0:
            game["totalHoursPlayed"] = float(round(stats["total_hours"], 1))
            game["numPlayers"] = int(stats["user_count"])
        else:
            game["totalHoursPlayed"] = 0.0
            game["numPlayers"] = 0
            
        if (i + 1) % 1000 == 0 or (i + 1) == len(games_list):
            print(f"   -> Processed playtime stats for {i + 1}/{len(games_list)} games...", end='\r')
    print()

    print("\n5. Generating Social Graph (Friend Requests & Mutual Friendships)...")
    user_map = {u["_id"]["$oid"]: u for u in users_list}
    all_user_ids = list(user_map.keys())
    established_friendships = set()
    pending_requests = set()

    for i, user in enumerate(users_list):
        u_id = user["_id"]["$oid"]
        
        valid_cands = [oid for oid in all_user_ids if oid != u_id 
                       and frozenset([u_id, oid]) not in established_friendships 
                       and (u_id, oid) not in pending_requests 
                       and (oid, u_id) not in pending_requests]
        
        if not valid_cands: continue
        if(user["username"] == STATIC_USER):
            M = min(MAX_FRIENDS, len(valid_cands))
            N = M
        else:
            M = random.randint(0, min(MAX_FRIENDS, len(valid_cands)))
            N = random.randint(0, M)
        if M == 0: continue
            
        
        selected_ids = random.sample(valid_cands, M)
        req_targets = selected_ids[:N]
        friend_targets = selected_ids[N:]

        for t_id in req_targets:
            user_map[t_id]["friendRequests"].append({
                "user_id": u_id, "username": user["username"],
                "pfpURL": user["pfpURL"], "timestamp": generate_recent_timestamp()
            })
            pending_requests.add((u_id, t_id))
            user_map[t_id]["requestsNum"] += 1 

        for t_id in friend_targets:
            established_friendships.add(frozenset([u_id, t_id]))
            user_map[u_id]["friends"] += 1
            user_map[t_id]["friends"] += 1

        if (i + 1) % 1000 == 0 or (i + 1) == len(users_list):
            print(f"   -> Processed social graph for {i + 1}/{len(users_list)} users...", end='\r')
    print()

    friend_rels = [{"id1": list(pair)[0], "id2": list(pair)[1]} for pair in established_friendships]

    print("   -> Sorting friend requests chronologically...")
    for user in users_list:
        if user["friendRequests"]:
            user["friendRequests"].sort(key=lambda x: x["timestamp"], reverse=True)

    print("\n6. Connecting to Neo4j to upload Graph Database...")
    neo4j_games = [{"id": g["_id"]["$oid"], "name": g.get("name",""), "achievements": g.get("achievements",0), "image": g.get("image","")} for g in games_list]
    
    neo4j_users = [{"id": u["_id"]["$oid"], "username": u["username"], "pfpURL": u["pfpURL"]} for u in users_list]

    try:
        driver = GraphDatabase.driver(NEO4J_URI, auth=(NEO4J_USER, NEO4J_PASSWORD))
            
        with driver.session() as session:
            clear_neo4j(session)
            

            manage_neo4j_indexes(session, "CREATE")
            
            print("   -> Inserting Game Nodes...")
            session.run("UNWIND $games AS game CREATE (g:Game {id: game.id, name: game.name, achievements: game.achievements, image: game.image})", games=neo4j_games)
            
            print("   -> Inserting User Nodes...")
            session.run("UNWIND $users AS user CREATE (u:User {id: user.id, username: user.username, pfpURL: user.pfpURL})", users=neo4j_users)
            
            print(f"   -> Inserting {len(played_relationships)} [:PLAYED] relationships...")
            for i in range(0, len(played_relationships), 10000):
                session.run("UNWIND $rels AS rel MATCH (u:User {id: rel.user_id}) MATCH (g:Game {id: rel.game_id}) CREATE (u)-[:PLAYED {hoursPlayed: rel.hoursPlayed, achievements: rel.achievements}]->(g)", rels=played_relationships[i:i+10000])

            print(f"   -> Inserting {len(friend_rels)} mutual [:FRIENDS_WITH] relationship pairs...")
            for i in range(0, len(friend_rels), 5000):
                session.run("UNWIND $pairs AS pair MATCH (u1:User {id: pair.id1}) MATCH (u2:User {id: pair.id2}) MERGE (u1)-[:FRIENDS_WITH]->(u2) MERGE (u2)-[:FRIENDS_WITH]->(u1)", pairs=friend_rels[i:i+5000])
            
        driver.close()
        print("   -> Neo4j upload completed successfully!")
    except Exception as e:
        print(f"   -> ERROR during Neo4j operations: {e}")
            
        driver.close()
        print("   -> Neo4j upload completed successfully!")
    except Exception as e:
        print(f"   -> ERROR during Neo4j operations: {e}")

    upload_to_mongodb(games_list, users_list, all_global_reviews)

    print("\n=== PIPELINE FINISHED SUCCESSFULLY! ===")

if __name__ == "__main__":
    main()
