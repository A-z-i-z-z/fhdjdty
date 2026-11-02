import Mathlib

open Finset

theorem e_abs_tri (a b : ℝ) : |a + b| ≤ |a| + |b| := by
  rcases abs_cases a with ⟨h1, h1'⟩ | ⟨h1, h1'⟩ <;>
  rcases abs_cases b with ⟨h2, h2'⟩ | ⟨h2, h2'⟩ <;>
  rcases abs_cases (a + b) with ⟨h3, h3'⟩ | ⟨h3, h3'⟩ <;>
  linarith

#print axioms e_abs_tri
