import Mathlib

open Finset

theorem e_set_distrib {α : Type*} (A B C : Set α) : A ∩ (B ∪ C) = (A ∩ B) ∪ (A ∩ C) := by
  ext x
  simp only [Set.mem_inter_iff, Set.mem_union]
  tauto

#print axioms e_set_distrib
