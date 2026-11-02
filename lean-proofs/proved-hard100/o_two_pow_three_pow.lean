import Mathlib

open Finset

theorem o_two_pow_three_pow (m n : ℕ) (h : 2 ^ m + 1 = 3 ^ n) : (m = 1 ∧ n = 1) ∨ (m = 3 ∧ n = 2) := by
  have h3 : ∀ k : ℕ, 3 ^ k % 80 = 1 ∨ 3 ^ k % 80 = 3 ∨ 3 ^ k % 80 = 9 ∨ 3 ^ k % 80 = 27 := by
    intro k
    induction k with
    | zero => norm_num
    | succ k ih =>
      have e : 3 ^ (k + 1) = 3 ^ k * 3 := pow_succ 3 k
      omega
  have h2 : ∀ k : ℕ, 2 ^ (k + 4) % 80 = 16 ∨ 2 ^ (k + 4) % 80 = 32 ∨ 2 ^ (k + 4) % 80 = 64 ∨ 2 ^ (k + 4) % 80 = 48 := by
    intro k
    induction k with
    | zero => norm_num
    | succ k ih =>
      have e : 2 ^ (k + 1 + 4) = 2 ^ (k + 4) * 2 := by ring
      omega
  rcases Nat.lt_or_ge m 4 with hm | hm
  · have hn : n < 4 := by
      by_contra hc
      have h81 : 3 ^ 4 ≤ 3 ^ n := Nat.pow_le_pow_right (by norm_num) (by omega)
      have hb : 2 ^ m ≤ 2 ^ 3 := Nat.pow_le_pow_right (by norm_num) (by omega)
      simp only [Nat.reducePow] at hb h81
      omega
    interval_cases m <;> interval_cases n <;> simp_all
  · exfalso
    obtain ⟨j, rfl⟩ : ∃ j, m = j + 4 := ⟨m - 4, by omega⟩
    have a := h2 j
    have b := h3 n
    omega

#print axioms o_two_pow_three_pow
