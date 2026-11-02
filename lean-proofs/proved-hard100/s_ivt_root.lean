import Mathlib

open Finset

theorem s_ivt_root : ∃ x : ℝ, 0 ≤ x ∧ x ≤ 1 ∧ x ^ 3 + x - 1 = 0 := by
  have hcont : ContinuousOn (fun x : ℝ => x ^ 3 + x - 1) (Set.Icc (0:ℝ) 1) := by
    fun_prop
  have hsub := intermediate_value_Icc (by norm_num : (0:ℝ) ≤ 1) hcont
  have h0v : (0:ℝ) ∈ Set.Icc ((fun x : ℝ => x ^ 3 + x - 1) 0) ((fun x : ℝ => x ^ 3 + x - 1) 1) := by
    simp only [Set.mem_Icc]
    norm_num
  obtain ⟨x, hx, hfx⟩ := hsub h0v
  exact ⟨x, hx.1, hx.2, hfx⟩

#print axioms s_ivt_root
