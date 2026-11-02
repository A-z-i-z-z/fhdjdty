import Mathlib

open Finset

theorem ml_succ_dvd_central_binom (n : ℕ) : (n + 1) ∣ Nat.centralBinom n := by
  first
    | exact Nat.succ_dvd_centralBinom n
    | exact Nat.succ_dvd_centralBinom
    | exact ⟨Nat.catalan n, (Nat.succ_mul_catalan_eq_centralBinom n).symm⟩

#print axioms ml_succ_dvd_central_binom
