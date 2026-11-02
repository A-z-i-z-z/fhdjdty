import Mathlib

open Finset

theorem e_even_sq (n : ℤ) (h : Even (n ^ 2)) : Even n := by
  rcases Int.even_or_odd n with hn | hn
  · exact hn
  · exfalso
    obtain ⟨k, hk⟩ := hn
    obtain ⟨m, hm⟩ := h
    have h2 : n ^ 2 = 2 * (2 * k ^ 2 + 2 * k) + 1 := by
      rw [hk]
      ring
    omega

#print axioms e_even_sq
