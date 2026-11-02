import Mathlib

open Finset

theorem e_inj_comp {α β γ : Type*} (f : α → β) (g : β → γ) (hf : Function.Injective f) (hg : Function.Injective g) : Function.Injective (g ∘ f) := by
  intro a b h
  apply hf
  apply hg
  exact h

#print axioms e_inj_comp
