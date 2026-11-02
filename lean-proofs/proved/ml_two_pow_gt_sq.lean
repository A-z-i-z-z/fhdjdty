import Mathlib

open Finset

theorem ml_two_pow_gt_sq (n : ℕ) (hn : 5 ≤ n) : n ^ 2 < 2 ^ n := by
  induction n, hn using Nat.le_induction with
  | base => norm_num
  | succ k hk ih =>
    have h2 : (k + 1) ^ 2 = k ^ 2 + 2 * k + 1 := by ring
    have h3 : 2 ^ (k + 1) = 2 * 2 ^ k := by ring
    have h1 : 2 * k + 1 ≤ k ^ 2 := by nlinarith [hk]
    rw [h2, h3]
    linarith

#print axioms ml_two_pow_gt_sq
