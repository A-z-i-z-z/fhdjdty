import Mathlib

open Finset

theorem s_pairs_choose (n : ℕ) : ((range n ×ˢ range n).filter (fun p => p.1 < p.2)).card = n.choose 2 := by
  have key : ∀ m : ℕ, ((range m ×ˢ range m).filter (fun p : ℕ × ℕ => p.1 < p.2)).card = ∑ j ∈ range m, j := by
    intro m
    induction m with
    | zero => simp
    | succ k ih =>
      have hs : (range (k+1) ×ˢ range (k+1)).filter (fun p : ℕ × ℕ => p.1 < p.2)
          = ((range k ×ˢ range k).filter (fun p : ℕ × ℕ => p.1 < p.2)) ∪ (range k).image (fun a => (a, k)) := by
        ext ⟨a, b⟩
        simp only [mem_filter, mem_product, mem_range, mem_union, mem_image, Prod.mk.injEq]
        constructor
        · rintro ⟨⟨ha, hb⟩, hab⟩
          by_cases hbk : b < k
          · exact Or.inl ⟨⟨by omega, hbk⟩, hab⟩
          · exact Or.inr ⟨a, by omega, rfl, by omega⟩
        · rintro (⟨⟨ha, hb⟩, hab⟩ | ⟨c, hc, rfl, rfl⟩)
          · exact ⟨⟨by omega, by omega⟩, hab⟩
          · exact ⟨⟨by omega, by omega⟩, hc⟩
      have hd : Disjoint ((range k ×ˢ range k).filter (fun p : ℕ × ℕ => p.1 < p.2))
          ((range k).image (fun a => (a, k))) := by
        rw [disjoint_left]
        rintro ⟨a, b⟩ h1 h2
        simp only [mem_filter, mem_product, mem_range, mem_image, Prod.mk.injEq] at h1 h2
        obtain ⟨c, hc, rfl, hb⟩ := h2
        omega
      have inj : Function.Injective (fun a : ℕ => (a, k)) := fun x y h => by simpa using h
      rw [hs, card_union_of_disjoint hd, ih, card_image_of_injective _ inj, card_range, sum_range_succ]
  rw [key n, Nat.choose_two_right]
  have h2 := Finset.sum_range_id_mul_two n
  rw [← h2]
  omega

#print axioms s_pairs_choose
