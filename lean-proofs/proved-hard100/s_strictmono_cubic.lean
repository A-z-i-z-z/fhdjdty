import Mathlib

open Finset

theorem s_strictmono_cubic : StrictMono (fun x : ℝ => x ^ 3 + x) := by
  intro a b hab
  show a ^ 3 + a < b ^ 3 + b
  have h : 0 < a ^ 2 + a * b + b ^ 2 + 1 := by
    nlinarith [sq_nonneg (a + b), sq_nonneg a, sq_nonneg b]
  nlinarith [mul_pos (sub_pos.mpr hab) h]

#print axioms s_strictmono_cubic
