import Mathlib

open Finset

theorem s_pigeonhole (f : Fin 5 → Fin 4) : ∃ i j, i ≠ j ∧ f i = f j := by
  exact Fintype.exists_ne_map_eq_of_card_lt f (by simp)

#print axioms s_pigeonhole
