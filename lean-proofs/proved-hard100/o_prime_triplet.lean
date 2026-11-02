import Mathlib

open Finset

theorem o_prime_triplet (p : ℕ) (hp : p.Prime) (h1 : (p + 2).Prime) (h2 : (p + 4).Prime) : p = 3 := by
  have hp2 := hp.two_le
  have key : p % 3 = 0 ∨ p % 3 = 1 ∨ p % 3 = 2 := by omega
  rcases key with h | h | h
  · have h3 : 3 ∣ p := by omega
    have := (Nat.prime_dvd_prime_iff_eq Nat.prime_three hp).mp h3
    omega
  · have h3 : 3 ∣ p + 2 := by omega
    have := (Nat.prime_dvd_prime_iff_eq Nat.prime_three h1).mp h3
    omega
  · have h3 : 3 ∣ p + 4 := by omega
    have := (Nat.prime_dvd_prime_iff_eq Nat.prime_three h2).mp h3
    omega

#print axioms o_prime_triplet
