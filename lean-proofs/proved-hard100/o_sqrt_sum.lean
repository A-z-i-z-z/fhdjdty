import Mathlib

open Finset

theorem o_sqrt_sum (a b : ℝ) (ha : 0 ≤ a) (hb : 0 ≤ b) : Real.sqrt a + Real.sqrt b ≤ Real.sqrt (2 * (a + b)) := by
  have ha' : Real.sqrt a ^ 2 = a := Real.sq_sqrt ha
  have hb' : Real.sqrt b ^ 2 = b := Real.sq_sqrt hb
  have hsa : 0 ≤ Real.sqrt a := Real.sqrt_nonneg a
  have hsb : 0 ≤ Real.sqrt b := Real.sqrt_nonneg b
  have key : (Real.sqrt a + Real.sqrt b) ^ 2 ≤ 2 * (a + b) := by
    nlinarith [sq_nonneg (Real.sqrt a - Real.sqrt b), ha', hb']
  calc Real.sqrt a + Real.sqrt b
      = Real.sqrt ((Real.sqrt a + Real.sqrt b) ^ 2) := (Real.sqrt_sq (by positivity)).symm
    _ ≤ Real.sqrt (2 * (a + b)) := Real.sqrt_le_sqrt key

#print axioms o_sqrt_sum
