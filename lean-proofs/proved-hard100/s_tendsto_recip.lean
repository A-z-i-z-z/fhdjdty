import Mathlib

open Finset

theorem s_tendsto_recip : Filter.Tendsto (fun n : ℕ => (1 : ℝ) / ((n : ℝ) + 1)) Filter.atTop (nhds 0) :=
  tendsto_one_div_add_atTop_nhds_zero_nat

#print axioms s_tendsto_recip
