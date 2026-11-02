import Mathlib

open Finset

theorem o_prime_sq_add2 (p : ℕ) (hp : p.Prime) (h : (p ^ 2 + 2).Prime) : p = 3 := by
  have h2 : 2 ≤ p := hp.two_le
  by_cases h3 : p % 3 = 0
  · have h3p : 3 ∣ p := Nat.dvd_of_mod_eq_zero h3
    rcases Nat.Prime.eq_one_or_self_of_dvd hp 3 h3p with h4 | h4
    · omega
    · omega
  · have hmod : p % 3 = 1 ∨ p % 3 = 2 := by omega
    have hdvd : 3 ∣ p ^ 2 + 2 := by
      apply Nat.dvd_of_mod_eq_zero
      rcases hmod with h5 | h5 <;> simp [Nat.add_mod, Nat.pow_mod, h5]
    rcases Nat.Prime.eq_one_or_self_of_dvd h 3 hdvd with h4 | h4
    · omega
    · nlinarith

#print axioms o_prime_sq_add2
