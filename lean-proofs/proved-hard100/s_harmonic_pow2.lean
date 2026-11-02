import Mathlib

open Finset

theorem s_harmonic_pow2_aux (n : ℕ) : 1 + (n : ℝ) / 2 ≤ ∑ k ∈ range (2 ^ n), (1 : ℝ) / ((k : ℝ) + 1) := by
  induction n with
  | zero => norm_num [Finset.sum_range_one]
  | succ m ih =>
    have hsplit : 2 ^ (m + 1) = 2 ^ m + 2 ^ m := by ring
    have hsplit_sum : ∑ k ∈ range (2 ^ (m + 1)), (1 : ℝ) / ((k : ℝ) + 1)
        = ∑ k ∈ range (2 ^ m), (1 : ℝ) / ((k : ℝ) + 1)
          + ∑ x ∈ range (2 ^ m), (1 : ℝ) / (((2 ^ m + x : ℕ) : ℝ) + 1) := by
      rw [hsplit, Finset.sum_range_add]
    have hlow : ∑ x ∈ range (2 ^ m), (1 : ℝ) / (((2 ^ m + x : ℕ) : ℝ) + 1)
        ≥ ∑ x ∈ range (2 ^ m), (1 : ℝ) / (2 : ℝ) ^ (m + 1) := by
      apply Finset.sum_le_sum
      intro x hx
      have hx' : x < 2 ^ m := Finset.mem_range.mp hx
      have h1 : 2 ^ m + x + 1 ≤ 2 ^ (m + 1) := by
        rw [hsplit]
        omega
      have h2 : (((2 ^ m + x : ℕ) : ℝ) + 1) ≤ (2 : ℝ) ^ (m + 1) := by
        exact_mod_cast h1
      have hpos : (0 : ℝ) < ((2 ^ m + x : ℕ) : ℝ) + 1 := by
        positivity
      exact one_div_le_one_div_of_le hpos h2
    have hconst : ∑ x ∈ range (2 ^ m), (1 : ℝ) / (2 : ℝ) ^ (m + 1) = 1 / 2 := by
      simp only [Finset.sum_const, Finset.card_range, nsmul_eq_mul, Nat.cast_pow, Nat.cast_ofNat]
      rw [pow_succ]
      field_simp
    rw [hsplit_sum, Nat.cast_succ]
    linarith [ih, hlow, hconst]

theorem s_harmonic_pow2 (n : ℕ) : 1 + (n : ℝ) / 2 ≤ ∑ k ∈ range (2 ^ n), (1 : ℝ) / ((k : ℝ) + 1) :=
  s_harmonic_pow2_aux n

#print axioms s_harmonic_pow2
