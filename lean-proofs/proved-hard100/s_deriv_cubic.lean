import Mathlib

open Finset

theorem s_deriv_cubic : deriv (fun x : ℝ => x ^ 3 + 2 * x) 1 = 5 := by
  have h : HasDerivAt (fun x : ℝ => x ^ 3 + 2 * x) 5 1 := by
    have h1 : HasDerivAt (fun x : ℝ => x ^ 3) (3 * (1:ℝ) ^ 2) 1 := by
      simpa using hasDerivAt_pow 3 (1:ℝ)
    have h2 : HasDerivAt (fun x : ℝ => 2 * x) 2 1 := by
      simpa using (hasDerivAt_id (1:ℝ)).const_mul 2
    convert h1.add h2 using 1 <;> norm_num
  exact h.deriv

#print axioms s_deriv_cubic
