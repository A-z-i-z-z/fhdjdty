import Mathlib

open Finset

theorem s_divisors_360 : (Nat.divisors 360).card = 24 := by
  first
  | decide
  | rfl
  | norm_num [Nat.divisors]

#print axioms s_divisors_360
