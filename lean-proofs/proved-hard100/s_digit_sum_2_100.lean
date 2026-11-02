import Mathlib

open Finset

theorem s_digit_sum_2_100 : (Nat.digits 10 (2 ^ 100)).sum = 115 := by
  first
    | norm_num
    | norm_num [Nat.digits_add_two_add_one]
    | simp [Nat.digits_add_two_add_one]
    | decide

#print axioms s_digit_sum_2_100
