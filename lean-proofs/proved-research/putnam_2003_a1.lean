import Mathlib

open Finset

open MvPolynomial
-- Note: uses (ℕ → ℤ) instead of (Fin k → ℤ)
abbrev putnam_2003_a1_solution : ℕ → ℕ := fun n => n

theorem putnam_2003_a1_aux_cnt (k s : ℕ) :
    ∑ i ∈ range k, (if s ≤ i then (1:ℤ) else 0) = ((k - s : ℕ) : ℤ) := by
  rw [Finset.sum_boole]
  have hf : (range k).filter (fun i => s ≤ i) = Ico s k := by
    ext i
    simp only [Finset.mem_filter, Finset.mem_range, Finset.mem_Ico]
    omega
  rw [hf, Nat.card_Ico]

theorem putnam_2003_a1 (n : ℕ) (hn : n > 0)
: Set.encard {a : ℕ → ℤ |
  ∃ k > 0, (∑ i : Fin k, a i = n) ∧
    (∀ i : Fin k, a i > 0) ∧
    (∀ i : Fin (k - 1), a i ≤ a (i + 1)) ∧
    a (k - 1) ≤ a 0 + 1 ∧ (∀ i ≥ k, a i = 0)}
  = putnam_2003_a1_solution n := by
  classical
  set F : ℕ → ℕ → ℤ := fun k i =>
    if i < k then ((n / k : ℕ) : ℤ) + (if k - n % k ≤ i then 1 else 0) else 0 with hF
  have hinj : Set.InjOn F (↑(Finset.Icc 1 n) : Set ℕ) := by
    intro k hk k' hk' h
    have hk2 := Finset.mem_Icc.mp (Finset.mem_coe.mp hk)
    have hk2' := Finset.mem_Icc.mp (Finset.mem_coe.mp hk')
    obtain ⟨hk1, hkn⟩ := hk2
    obtain ⟨hk1', hk'n⟩ := hk2'
    by_contra hne
    rcases Nat.lt_or_gt_of_ne hne with hlt | hlt
    · have h1 := congrFun h k
      have hz : F k k = 0 := by simp [hF]
      have hq : 0 < n / k' := Nat.div_pos hk'n (by omega)
      have hqz : (0:ℤ) < ((n / k' : ℕ) : ℤ) := by exact_mod_cast hq
      have hp : (0:ℤ) < F k' k := by
        simp only [hF, if_pos hlt]
        split_ifs <;> linarith
      linarith
    · have hlt' : k' < k := hlt
      have h1 := congrFun h k'
      have hz : F k' k' = 0 := by simp [hF]
      have hq : 0 < n / k := Nat.div_pos hkn (by omega)
      have hqz : (0:ℤ) < ((n / k : ℕ) : ℤ) := by exact_mod_cast hq
      have hp : (0:ℤ) < F k k' := by
        simp only [hF, if_pos hlt']
        split_ifs <;> linarith
      linarith
  have key : {a : ℕ → ℤ | ∃ k > 0, (∑ i : Fin k, a i = n) ∧
    (∀ i : Fin k, a i > 0) ∧
    (∀ i : Fin (k - 1), a i ≤ a (i + 1)) ∧
    a (k - 1) ≤ a 0 + 1 ∧ (∀ i ≥ k, a i = 0)}
      = ((Finset.Icc 1 n).image F : Set (ℕ → ℤ)) := by
    ext a
    rw [Set.mem_setOf_eq, Finset.mem_coe, Finset.mem_image]
    constructor
    · rintro ⟨k, hk, hsum, hpos, hmono', hlast, hzero⟩
      have mono_step : ∀ m, m < k - 1 → a m ≤ a (m + 1) := fun m hm => hmono' ⟨m, hm⟩
      have mono : ∀ i d, i + d < k → a i ≤ a (i + d) := by
        intro i d
        induction d with
        | zero => intro _; simp
        | succ d ih =>
          intro hd
          have h1 := ih (by omega)
          have h2 := mono_step (i + d) (by omega)
          rw [show i + (d + 1) = i + d + 1 by omega]
          linarith
      have h0pos : (0:ℤ) < a 0 := hpos ⟨0, hk⟩
      obtain ⟨c, hc⟩ : ∃ c : ℕ, a 0 = (c:ℤ) :=
        ⟨(a 0).toNat, (Int.toNat_of_nonneg h0pos.le).symm⟩
      have hc1 : 1 ≤ c := by
        have h := h0pos
        rw [hc] at h
        omega
      have hmin : ∀ i, i < k → (c:ℤ) ≤ a i := by
        intro i hi
        have h := mono 0 i (by omega)
        rw [zero_add] at h
        rw [← hc]
        exact h
      have hmax : ∀ i, i < k → a i ≤ (c:ℤ) + 1 := by
        intro i hi
        have h := mono i (k - 1 - i) (by omega)
        rw [show i + (k - 1 - i) = k - 1 by omega] at h
        rw [← hc]
        linarith
      have hsum' : ∑ i ∈ range k, a i = (n:ℤ) := by
        rw [← Fin.sum_univ_eq_sum_range a k]
        exact hsum
      have hex : ∃ j : ℕ, (a j = (c:ℤ) + 1 ∨ k ≤ j) := ⟨k, Or.inr le_rfl⟩
      obtain ⟨s, hs1, hs2⟩ : ∃ s : ℕ, (a s = (c:ℤ) + 1 ∨ k ≤ s) ∧
          ∀ j < s, ¬ (a j = (c:ℤ) + 1 ∨ k ≤ j) :=
        ⟨Nat.find hex, Nat.find_spec hex, fun j hj => Nat.find_min hex hj⟩
      have hsk : s ≤ k := by
        by_contra h
        exact hs2 k (by omega) (Or.inr le_rfl)
      have hs1pos : 1 ≤ s := by
        rcases Nat.eq_zero_or_pos s with h0 | h0
        · exfalso
          rw [h0] at hs1
          rcases hs1 with h1 | h1
          · rw [hc] at h1
            linarith
          · omega
        · omega
      have hval : ∀ i, i < k → a i = (c:ℤ) + (if s ≤ i then 1 else 0) := by
        intro i hi
        by_cases his : s ≤ i
        · rw [if_pos his]
          have hs_eq : a s = (c:ℤ) + 1 := hs1.resolve_right (by omega)
          have h2 := mono s (i - s) (by omega)
          rw [show s + (i - s) = i by omega] at h2
          have h3 := hmax i hi
          linarith
        · rw [if_neg his, add_zero]
          have hne : a i ≠ (c:ℤ) + 1 := fun h => hs2 i (by omega) (Or.inl h)
          have hlt : a i < (c:ℤ) + 1 := lt_of_le_of_ne (hmax i hi) hne
          have hlo := hmin i hi
          omega
      have hT : ∑ i ∈ range k, a i = ∑ i ∈ range k, ((c:ℤ) + (if s ≤ i then 1 else 0)) :=
        Finset.sum_congr rfl (fun i hi => hval i (Finset.mem_range.mp hi))
      rw [Finset.sum_add_distrib, putnam_2003_a1_aux_cnt, Finset.sum_const,
        Finset.card_range, nsmul_eq_mul] at hT
      have key2 : (n:ℤ) = (k:ℤ) * c + ((k - s : ℕ) : ℤ) := by linarith
      have hnat : n = k * c + (k - s) := by exact_mod_cast key2
      have hr : k - s < k := by omega
      have hdiv : n = (k - s) + k * c := by rw [hnat, Nat.add_comm]
      have hqc : n / k = c := by
        rw [hdiv, Nat.add_mul_div_left _ _ (by omega : 0 < k), Nat.div_eq_of_lt hr, zero_add]
      have hrc : n % k = k - s := by
        rw [hdiv, Nat.add_mul_mod_self_left, Nat.mod_eq_of_lt hr]
      have hkn : k ≤ n := by
        have hm := Nat.mul_le_mul (le_refl k) hc1
        linarith
      have hfun : F k = a := by
        funext i
        by_cases hi : i < k
        · have e1 : k - (k - s) = s := by omega
          simp only [hF, if_pos hi, hqc, hrc, e1]
          rw [hval i hi]
        · have hik : ¬ i < k := hi
          simp [hF, hik, hzero i (by omega)]
      exact ⟨k, Finset.mem_Icc.mpr ⟨by omega, hkn⟩, hfun⟩
    · rintro ⟨k, hkmem, rfl⟩
      obtain ⟨hk1, hkn⟩ := Finset.mem_Icc.mp hkmem
      have hk0 : 0 < k := by omega
      have hr : n % k < k := Nat.mod_lt _ hk0
      have hq : 0 < n / k := Nat.div_pos hkn hk0
      have hqz : (0:ℤ) < ((n / k : ℕ) : ℤ) := by exact_mod_cast hq
      have hsumF : ∑ i : Fin k, F k ↑i = (n:ℤ) := by
        rw [Fin.sum_univ_eq_sum_range (F k) k]
        have e1 : ∑ i ∈ range k, F k i
            = ∑ i ∈ range k, (((n / k : ℕ) : ℤ) + (if k - n % k ≤ i then (1:ℤ) else 0)) := by
          apply Finset.sum_congr rfl
          intro i hi
          have hi' := Finset.mem_range.mp hi
          simp only [hF, if_pos hi']
        rw [e1, Finset.sum_add_distrib, putnam_2003_a1_aux_cnt, Finset.sum_const,
          Finset.card_range, nsmul_eq_mul]
        have e2 : k - (k - n % k) = n % k := by omega
        rw [e2]
        have h := Nat.div_add_mod n k
        generalize n / k = q at h ⊢
        generalize n % k = r at h ⊢
        exact_mod_cast h
      have hpos : ∀ i : Fin k, F k ↑i > 0 := by
        intro i
        have hi := i.isLt
        simp only [hF, if_pos hi]
        split_ifs <;> linarith
      have hmono : ∀ i : Fin (k - 1), F k ↑i ≤ F k (↑i + 1) := by
        intro i
        have hi1 : (i:ℕ) < k := by have := i.isLt; omega
        have hi2 : (i:ℕ) + 1 < k := by have := i.isLt; omega
        simp only [hF, if_pos hi1, if_pos hi2]
        split_ifs with h1 h2 <;> first | linarith | (exfalso; omega)
      have hlast : F k (k - 1) ≤ F k 0 + 1 := by
        have h1 : k - 1 < k := by omega
        simp only [hF, if_pos hk0, if_pos h1]
        split_ifs <;> linarith
      have hzero : ∀ i ≥ k, F k i = 0 := by
        intro i hi
        have hik : ¬ i < k := by omega
        simp [hF, hik]
      exact ⟨k, hk0, hsumF, hpos, hmono, hlast, hzero⟩
  rw [key, Set.encard_coe_eq_coe_finsetCard, Finset.card_image_of_injOn hinj, Nat.card_Icc]
  simp [putnam_2003_a1_solution]

#print axioms putnam_2003_a1
