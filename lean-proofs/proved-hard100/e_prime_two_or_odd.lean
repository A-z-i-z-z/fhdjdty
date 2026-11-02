import Mathlib

open Finset

theorem e_prime_two_or_odd (p : ℕ) (hp : p.Prime) : p = 2 ∨ Odd p := by
  rcases hp.eq_two_or_odd with h | h
  · exact Or.inl h
  · exact Or.inr (Nat.odd_iff.mpr h)

#print axioms e_prime_two_or_odd
