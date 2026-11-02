import Mathlib

open Finset

theorem s_schur (a b c : ℝ) (ha : 0 ≤ a) (hb : 0 ≤ b) (hc : 0 ≤ c) : 0 ≤ a * (a - b) * (a - c) + b * (b - a) * (b - c) + c * (c - a) * (c - b) := by
  rcases le_total a b with h1 | h1 <;>
  rcases le_total b c with h2 | h2 <;>
  rcases le_total a c with h3 | h3 <;>
  nlinarith [mul_nonneg (mul_nonneg ha (sub_nonneg.2 h1)) (sub_nonneg.2 h3),
    mul_nonneg (mul_nonneg hb (sub_nonneg.2 h1)) (sub_nonneg.2 h2),
    mul_nonneg (mul_nonneg hc (sub_nonneg.2 h2)) (sub_nonneg.2 h3),
    mul_nonneg (mul_nonneg ha (sub_nonneg.2 h2)) (sub_nonneg.2 h3),
    mul_nonneg (mul_nonneg hb (sub_nonneg.2 h2)) (sub_nonneg.2 h3),
    mul_nonneg (mul_nonneg hc (sub_nonneg.2 h1)) (sub_nonneg.2 h3),
    mul_nonneg (mul_nonneg hc (sub_nonneg.2 h1)) (sub_nonneg.2 h2),
    mul_nonneg (mul_nonneg ha (sub_nonneg.2 h1)) (sub_nonneg.2 h2),
    mul_nonneg (mul_nonneg hb (sub_nonneg.2 h1)) (sub_nonneg.2 h3),
    mul_nonneg (sq_nonneg (a - b)) ha,
    mul_nonneg (sq_nonneg (a - b)) hb,
    mul_nonneg (sq_nonneg (a - b)) hc,
    mul_nonneg (sq_nonneg (b - c)) ha,
    mul_nonneg (sq_nonneg (b - c)) hb,
    mul_nonneg (sq_nonneg (b - c)) hc,
    mul_nonneg (sq_nonneg (a - c)) ha,
    mul_nonneg (sq_nonneg (a - c)) hb,
    mul_nonneg (sq_nonneg (a - c)) hc,
    mul_nonneg (sq_nonneg (a - b)) (sub_nonneg.2 h2),
    mul_nonneg (sq_nonneg (a - b)) (sub_nonneg.2 h3),
    mul_nonneg (sq_nonneg (b - c)) (sub_nonneg.2 h1),
    mul_nonneg (sq_nonneg (b - c)) (sub_nonneg.2 h3),
    mul_nonneg (sq_nonneg (a - c)) (sub_nonneg.2 h1),
    mul_nonneg (sq_nonneg (a - c)) (sub_nonneg.2 h2)]

#print axioms s_schur
