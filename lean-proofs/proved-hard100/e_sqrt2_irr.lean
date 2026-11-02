import Mathlib

open Finset

theorem e_sqrt2_irr : Irrational (Real.sqrt 2) := by
  first
  | exact irrational_sqrt_two
  | (have h := Nat.Prime.irrational_sqrt Nat.prime_two
     simpa using h)

#print axioms e_sqrt2_irr
