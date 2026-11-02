import Mathlib

open Finset

theorem ml_cb_succ_aux (n : ℕ) : Nat.centralBinom (n + 1) = 2 * Nat.choose (2 * n + 1) n := by
  have h1 : Nat.choose (2 * n + 1) (n + 1) = Nat.choose (2 * n + 1) n := by
    have h := Nat.choose_symm (show n + 1 ≤ 2 * n + 1 by omega)
    rw [show 2 * n + 1 - (n + 1) = n by omega] at h
    exact h.symm
  have h2 : Nat.choose (2 * n + 1 + 1) (n + 1) = Nat.choose (2 * n + 1) n + Nat.choose (2 * n + 1) (n + 1) :=
    Nat.choose_succ_succ (2 * n + 1) n
  show Nat.choose (2 * (n + 1)) (n + 1) = 2 * Nat.choose (2 * n + 1) n
  rw [show 2 * (n + 1) = 2 * n + 1 + 1 by omega, h2, h1]
  ring

theorem ml_central_binom_even (n : ℕ) (hn : 0 < n) : 2 ∣ Nat.centralBinom n := by
  obtain ⟨m, rfl⟩ : ∃ m, n = m + 1 := ⟨n - 1, by omega⟩
  rw [ml_cb_succ_aux]
  exact dvd_mul_right 2 _

#print axioms ml_central_binom_even
