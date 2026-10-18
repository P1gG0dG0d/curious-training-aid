"""Run with Python 3. Real SQLite SQL/schema test, not Android Room runtime instrumentation."""
import json
import re
import sqlite3
import unittest
from pathlib import Path

APP = Path(__file__).resolve().parents[3]

class DailyActivityMigrationTest(unittest.TestCase):
    def test_upgrade_preserves_legacy_data_and_starts_daily_table_empty(self):
        schema = json.loads((APP / 'schemas/com.haoqi.training.data.db.AppDatabase/3.json').read_text(encoding='utf-8'))['database']
        db = sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        for entity in schema['entities']:
            db.execute(entity['createSql'].replace('${TABLE_NAME}', entity['tableName']))
        db.execute("INSERT INTO profile VALUES(1,'MALE',30,175,70,777,1)")
        db.execute('INSERT INTO plans VALUES(1,1)')
        db.execute("INSERT INTO plan_days VALUES(1,1,1,1,'Legs')")
        db.execute("INSERT INTO food_entries VALUES(1,'2026-10-18','LUNCH','rice','100g',130,3,1,28,0)")
        source = (APP / 'src/main/java/com/haoqi/training/data/db/AppDatabase.kt').read_text(encoding='utf-8')
        sql = re.search(r'database.execSQL\("(CREATE TABLE IF NOT EXISTS `daily_activity`.*?)"\)', source).group(1)
        db.execute(sql)
        self.assertEqual(0, db.execute('SELECT count(*) FROM daily_activity').fetchone()[0])
        self.assertEqual(777, db.execute('SELECT activityKcal FROM profile').fetchone()[0])
        self.assertEqual(('Legs', 1), db.execute('SELECT name,completed FROM plan_days').fetchone())
        self.assertEqual(130, db.execute('SELECT kcal FROM food_entries').fetchone()[0])
        db.executemany('INSERT OR REPLACE INTO daily_activity VALUES (?,?)', [('2026-10-18',300),('2026-10-19',90),('2026-10-18',400)])
        self.assertEqual([('2026-10-18',400.0),('2026-10-19',90.0)], db.execute('SELECT * FROM daily_activity ORDER BY date').fetchall())
        v4 = json.loads((APP / 'schemas/com.haoqi.training.data.db.AppDatabase/4.json').read_text(encoding='utf-8'))['database']
        expected = sqlite3.connect(':memory:')
        self.addCleanup(expected.close)
        entity = next(e for e in v4['entities'] if e['tableName'] == 'daily_activity')
        expected.execute(entity['createSql'].replace('${TABLE_NAME}', 'daily_activity'))
        self.assertEqual(expected.execute('PRAGMA table_info(daily_activity)').fetchall(), db.execute('PRAGMA table_info(daily_activity)').fetchall())

if __name__ == '__main__':
    unittest.main()
