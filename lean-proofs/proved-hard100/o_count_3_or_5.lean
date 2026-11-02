import Mathlib

open Finset

theorem o_count_3_or_5 : ((range 1001).filter (fun n => 3 ∣ n ∨ 5 ∣ n)).card = 468 := by
  first
    | decide +kernel
    | rfl
    | decide

#print axioms o_count_3_or_5
