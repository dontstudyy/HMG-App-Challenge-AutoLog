"""tools/demo.py의 시작값 판단과 계획 검증 테스트(에뮬레이터 없이 실행). 실행: python -m unittest discover -s tools"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import demo  # noqa: E402


class DecideStartTest(unittest.TestCase):
    def test_기본은_현재_값에서_시작한다(self):
        for cur in (0.0, 4.9, 7000.0, 12321.0, 98765.4):
            start, note = demo.decide_start("current", cur)
            self.assertEqual(start, cur)
            self.assertIn("현재 누적거리", note)

    def test_연결이_없으면_목업_값으로_계획만_보여준다(self):
        start, note = demo.decide_start("current", None)
        self.assertEqual(start, demo.DEFAULT_START)
        self.assertIn("연결되지 않아", note)

    def test_숫자를_주면_그_값(self):
        start, _ = demo.decide_start("12321", 3.0)
        self.assertEqual(start, 12321.0)
        start, _ = demo.decide_start("12321", None)
        self.assertEqual(start, 12321.0)

    def test_현재_값보다_작은_시작값은_줄일_수_없어_현재_값에서_시작한다(self):
        start, note = demo.decide_start("12321", 15000.0)
        self.assertEqual(start, 15000.0)
        self.assertIn("줄일 수 없어서", note)

    def test_현재_값과_거의_같으면_요청값을_따른다(self):
        start, _ = demo.decide_start("100.0", 100.04)
        self.assertEqual(start, 100.0)


class PlanTest(unittest.TestCase):
    def test_trips_json은_합계_비율_연속성이_맞다(self):
        self.assertTrue(demo.verify_plan(demo.load_trips()))

    def test_어느_시작값에서도_합계와_비율은_같다(self):
        data = demo.load_trips()
        trips = data["trips"]
        for start in (0.0, 4.9, 12321.0, 77777.7):
            pos = start
            targets = []
            for r in trips:
                pos += r["dist"]
                targets.append(pos)
            self.assertAlmostEqual(targets[-1] - start, data["expect"]["afterTotal"])  # 155km 올림
            self.assertEqual(data["expect"]["afterRatio"], 85.8)


if __name__ == "__main__":
    unittest.main()
