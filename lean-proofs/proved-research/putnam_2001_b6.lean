import Mathlib

open Finset

open Topology Filter Polynomial Set
-- Note: uses (ℤ → ℝ) instead of (Set.Ici 1 → ℝ)
abbrev putnam_2001_b6_solution : Prop := True

lemma pb6_mono (a : ℤ → ℝ) (ha : ∀ n : ℤ, 1 ≤ n → a n < a (n + 1)) (j : ℤ) (hj : 1 ≤ j) :
    ∀ d : ℕ, a j ≤ a (j + (d : ℤ)) := by
  intro d
  induction d with
  | zero => simp
  | succ d ih =>
    have h1 := ha (j + (d : ℤ)) (by omega)
    have e : j + ((d + 1 : ℕ) : ℤ) = j + (d : ℤ) + 1 := by push_cast; ring
    rw [e]
    linarith

lemma pb6_mono' (a : ℤ → ℝ) (ha : ∀ n : ℤ, 1 ≤ n → a n < a (n + 1)) (j k : ℤ)
    (hj : 1 ≤ j) (hjk : j ≤ k) : a j ≤ a k := by
  have hd : ((k - j).toNat : ℤ) = k - j := Int.toNat_of_nonneg (by omega)
  have h1 := pb6_mono a ha j hj (k - j).toNat
  rw [hd] at h1
  have e : j + (k - j) = k := by ring
  rw [e] at h1
  exact h1

lemma pb6_argmax (c : ℤ → ℝ) (M : ℤ) (hM : 1 ≤ M) (hbig : ∀ k : ℤ, M < k → c k < c 1) :
    ∃ n : ℤ, 1 ≤ n ∧ n ≤ M ∧ (∀ k : ℤ, 1 ≤ k → c k ≤ c n) ∧
      (∀ k : ℤ, 1 ≤ k → k < n → c k < c n) := by
  classical
  obtain ⟨T, hTdef⟩ : ∃ T : Finset ℤ, T = (Finset.Icc (1:ℤ) M).filter
      (fun k => ∀ j ∈ Finset.Icc (1:ℤ) M, c j ≤ c k) := ⟨_, rfl⟩
  have hne : (Finset.Icc (1:ℤ) M).Nonempty := ⟨1, Finset.mem_Icc.mpr ⟨le_refl _, hM⟩⟩
  obtain ⟨x, hx, hxmax⟩ := Finset.exists_max_image (Finset.Icc (1:ℤ) M) c hne
  have hTne : T.Nonempty := ⟨x, by rw [hTdef, Finset.mem_filter]; exact ⟨hx, hxmax⟩⟩
  obtain ⟨n, hnT, hnmin⟩ : ∃ n, n ∈ T ∧ ∀ m ∈ T, n ≤ m :=
    ⟨T.min' hTne, Finset.min'_mem T hTne, fun m hm => Finset.min'_le T m hm⟩
  rw [hTdef, Finset.mem_filter] at hnT
  obtain ⟨hnI, hnmax⟩ := hnT
  have hnM : n ≤ M := (Finset.mem_Icc.mp hnI).2
  have hn1 : 1 ≤ n := (Finset.mem_Icc.mp hnI).1
  refine ⟨n, hn1, hnM, ?_, ?_⟩
  · intro k hk
    by_cases hkM : k ≤ M
    · exact hnmax k (Finset.mem_Icc.mpr ⟨hk, hkM⟩)
    · exact le_trans (le_of_lt (hbig k (by omega))) (hnmax 1 (Finset.mem_Icc.mpr ⟨le_refl _, hM⟩))
  · intro k hk hkn
    by_contra hcon
    push_neg at hcon
    have hkT : k ∈ T := by
      rw [hTdef, Finset.mem_filter]
      exact ⟨Finset.mem_Icc.mpr ⟨hk, by omega⟩, fun j hj => le_trans (hnmax j hj) hcon⟩
    have := hnmin k hkT
    omega

theorem putnam_2001_b6 :
  (∀ a : ℤ → ℝ,
    (∀ n ≥ 1, a n > 0 ∧ a n < a (n + 1)) →
    (Tendsto (fun n : ℤ => a (n + 1) / (n + 1)) atTop (𝓝 0)) →
    {n : ℤ | n > 0 ∧ (∀ i ∈ Set.Icc 1 (n - 1), a (n - i) + a (n + i) < 2 * a n)}.Infinite)
  ↔ putnam_2001_b6_solution := by
  refine ⟨fun _ => trivial, fun _ a ha hb => ?_⟩
  have key : ∀ K : ℤ, 1 ≤ K → ∃ n : ℤ,
      (n > 0 ∧ ∀ i ∈ Set.Icc 1 (n - 1), a (n - i) + a (n + i) < 2 * a n) ∧ K < n := by
    intro K hK
    have hKp : (0:ℝ) < ((K + 1 : ℤ) : ℝ) := by
      have hK1 : (0:ℤ) < K + 1 := by omega
      exact_mod_cast hK1
    have hKne : ((K + 1 : ℤ) : ℝ) ≠ 0 := hKp.ne'
    have hlt1 : a K < a (K + 1) := (ha K hK).2
    obtain ⟨t, ht0, ht1⟩ : ∃ t : ℝ, 0 < t ∧ t * ((K + 1 : ℤ) : ℝ) = a (K + 1) - a K :=
      ⟨(a (K + 1) - a K) * ((K + 1 : ℤ) : ℝ)⁻¹, mul_pos (by linarith) (inv_pos.mpr hKp),
        by rw [mul_assoc, inv_mul_cancel₀ hKne, mul_one]⟩
    have hmono : ∀ j : ℤ, 1 ≤ j → j ≤ K → a j ≤ a K :=
      fun j hj hjK => pb6_mono' a (fun n hn => (ha n hn).2) j K hj hjK
    have hjbound : ∀ j : ℤ, 1 ≤ j → j ≤ K →
        a j - t * (j : ℝ) < a (K + 1) - t * ((K + 1 : ℤ) : ℝ) := by
      intro j hj hjK
      have h1 : a j ≤ a K := hmono j hj hjK
      have hjr : (0:ℝ) < (j : ℝ) := by exact_mod_cast (by omega : (0:ℤ) < j)
      have h2 : 0 < t * (j : ℝ) := mul_pos ht0 hjr
      linarith
    obtain ⟨M0, hM0⟩ := Filter.eventually_atTop.mp
      (hb.eventually (gt_mem_nhds (half_pos ht0)))
    have hev : ∀ k : ℤ, M0 < k → a k / (k : ℝ) < t / 2 := by
      intro k hk
      have h1 : a (k - 1 + 1) / (((k - 1 : ℤ) : ℝ) + 1) < t / 2 := hM0 (k - 1) (by omega)
      have e : k - 1 + 1 = k := by ring
      have e2 : ((k - 1 : ℤ) : ℝ) + 1 = (k : ℝ) := by push_cast; ring
      rw [e, e2] at h1
      exact h1
    obtain ⟨M, hMa, hMb⟩ : ∃ M : ℤ, M0 ≤ M ∧ 2 ≤ M :=
      ⟨max M0 2, le_max_left _ _, le_max_right _ _⟩
    have hbig : ∀ k : ℤ, M < k → a k - t * (k : ℝ) < a 1 - t * ((1 : ℤ) : ℝ) := by
      intro k hk
      rw [Int.cast_one]
      have hA : a k / (k : ℝ) < t / 2 := hev k (by omega)
      have hk2 : (2 : ℝ) ≤ (k : ℝ) := by exact_mod_cast (by omega : (2 : ℤ) ≤ k)
      have hkpos : (0 : ℝ) < (k : ℝ) := by linarith
      have hA' : a k < t / 2 * (k : ℝ) := by
        rwa [div_lt_iff₀ hkpos] at hA
      have ha1 : 0 < a 1 := (ha 1 le_rfl).1
      nlinarith [mul_nonneg ht0.le (by linarith : (0 : ℝ) ≤ (k : ℝ) - 2)]
    obtain ⟨n, hn1, _, hnmax, hnlt⟩ :=
      pb6_argmax (fun k : ℤ => a k - t * (k : ℝ)) M (by omega) hbig
    have hnmax' : ∀ k : ℤ, 1 ≤ k → a k - t * (k : ℝ) ≤ a n - t * (n : ℝ) := hnmax
    have hnlt' : ∀ k : ℤ, 1 ≤ k → k < n → a k - t * (k : ℝ) < a n - t * (n : ℝ) := hnlt
    have hgt : K < n := by
      by_contra hcon
      push_neg at hcon
      have h1 := hnmax' (K + 1) (by omega)
      have h2 := hjbound n hn1 hcon
      linarith
    refine ⟨n, ⟨by omega, ?_⟩, hgt⟩
    intro i hi
    have hi1 : 1 ≤ i := hi.1
    have hi2 : i ≤ n - 1 := hi.2
    have c1 := hnlt' (n - i) (by omega) (by omega)
    have c2 := hnmax' (n + i) (by omega)
    push_cast at c1 c2
    linarith
  apply Set.infinite_of_not_bddAbove
  rw [not_bddAbove_iff]
  intro K0
  obtain ⟨n, hgood, hlt⟩ := key (max K0 1) (le_max_right _ _)
  refine ⟨n, ?_, lt_of_le_of_lt (le_max_left _ _) hlt⟩
  rw [Set.mem_setOf_eq]
  exact hgood

#print axioms putnam_2001_b6
