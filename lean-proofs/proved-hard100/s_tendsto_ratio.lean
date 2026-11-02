import Mathlib

open Finset

theorem s_tendsto_ratio : Filter.Tendsto (fun n : ℕ => ((n : ℝ) + 1) / (2 * (n : ℝ) + 3)) Filter.atTop (nhds (1 / 2)) := by
  have hA : Filter.Tendsto (fun n : ℕ => (2 * (n : ℝ) + 3)) Filter.atTop Filter.atTop := by
    refine Filter.tendsto_atTop_mono (fun n => ?_) tendsto_natCast_atTop_atTop
    show (n : ℝ) ≤ 2 * (n : ℝ) + 3
    have := (Nat.cast_nonneg n : (0 : ℝ) ≤ n)
    linarith
  have hB : Filter.Tendsto (fun n : ℕ => (2 * (n : ℝ) + 3)⁻¹) Filter.atTop (nhds 0) :=
    tendsto_inv_atTop_zero.comp hA
  have hC : Filter.Tendsto (fun n : ℕ => (1 / 2 : ℝ) - (1 / 2 : ℝ) * (2 * (n : ℝ) + 3)⁻¹)
      Filter.atTop (nhds ((1 / 2 : ℝ) - (1 / 2 : ℝ) * 0)) :=
    tendsto_const_nhds.sub (tendsto_const_nhds.mul hB)
  rw [show ((1 / 2 : ℝ) - (1 / 2 : ℝ) * 0) = 1 / 2 by norm_num] at hC
  refine Filter.Tendsto.congr (fun n => ?_) hC
  show (1 / 2 : ℝ) - (1 / 2 : ℝ) * (2 * (n : ℝ) + 3)⁻¹ = ((n : ℝ) + 1) / (2 * (n : ℝ) + 3)
  have hpos : (2 * (n : ℝ) + 3) ≠ 0 := by positivity
  have h : (2 * (n : ℝ) + 3) * (2 * (n : ℝ) + 3)⁻¹ = 1 := mul_inv_cancel₀ hpos
  linear_combination (-1 / 2 : ℝ) * h

#print axioms s_tendsto_ratio
