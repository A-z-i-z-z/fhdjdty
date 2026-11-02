import Mathlib

open Finset

theorem s_sophie_germain (n : ℕ) (h : 2 ≤ n) : ¬ Nat.Prime (n ^ 4 + 4) := by
  obtain ⟨k, rfl⟩ : ∃ k, n = k + 2 := ⟨n - 2, by omega⟩
  have hfac : (k + 2) ^ 4 + 4 = (k ^ 2 + 6 * k + 10) * (k ^ 2 + 2 * k + 2) := by
    ring
  rw [hfac]
  intro hp
  rcases Nat.prime_mul_iff.mp hp with ⟨_, h1⟩ | ⟨_, h1⟩
  · nlinarith [sq_nonneg k]
  · nlinarith [sq_nonneg k]

#print axioms s_sophie_germain
