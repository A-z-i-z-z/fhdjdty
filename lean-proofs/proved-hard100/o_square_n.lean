import Mathlib

open Finset

theorem o_square_n (n k : ℕ) (h : n ^ 2 + 2 * n + 12 = k ^ 2) : n = 4 := by
  have hk : n + 1 < k := by
    by_contra hc
    push_neg at hc
    have hsq : k ^ 2 ≤ (n + 1) ^ 2 := Nat.pow_le_pow_left hc 2
    nlinarith
  obtain ⟨d, rfl⟩ : ∃ d, k = n + 1 + d := ⟨k - (n + 1), by omega⟩
  have hd : 2 * d * (n + 1) + d * d = 11 := by
    nlinarith
  have hd3 : d ≤ 3 := by
    nlinarith
  interval_cases d <;> omega

#print axioms o_square_n
