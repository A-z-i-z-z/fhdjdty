import Mathlib

open Finset

open Topology Filter
abbrev putnam_2000_a1_solution : ℝ → Set ℝ := (fun A : ℝ => Set.Ioo 0 (A ^ 2))

theorem putnam_2000_a1 (A : ℝ)
(Apos : A > 0)
: {S : ℝ |
    ∃ x : ℕ → ℝ,
      (∀ j : ℕ, x j > 0) ∧
      (∑' j : ℕ, x j) = A ∧
      (∑' j : ℕ, (x j) ^ 2) = S}
  = putnam_2000_a1_solution A := by
  ext S
  simp only [Set.mem_setOf_eq, putnam_2000_a1_solution, Set.mem_Ioo]
  have hA0 : A ≠ 0 := Apos.ne'
  constructor
  · rintro ⟨x, hpos, hsum, hS⟩
    have hsx : Summable x := by
      by_contra h
      rw [tsum_eq_zero_of_not_summable h] at hsum
      linarith
    have hP : Tendsto (fun n => ∑ i ∈ range n, x i) atTop (𝓝 A) := by
      rw [← hsum]
      exact hsx.hasSum.tendsto_sum_nat
    have hmono : Monotone (fun n => ∑ i ∈ range n, x i) := by
      apply monotone_nat_of_le_succ
      intro n
      simp only [Finset.sum_range_succ]
      linarith [hpos n]
    have hle : ∀ n, ∑ i ∈ range n, x i ≤ A := hmono.ge_of_tendsto hP
    have hxA : ∀ j, x j ≤ A := by
      intro j
      have h1 := hle (j + 1)
      rw [Finset.sum_range_succ] at h1
      have h2 : 0 ≤ ∑ i ∈ range j, x i := Finset.sum_nonneg (fun i _ => le_of_lt (hpos i))
      linarith
    have hsq : Summable (fun j => x j ^ 2) :=
      Summable.of_nonneg_of_le (fun j => sq_nonneg (x j))
        (fun j => by
          show x j ^ 2 ≤ A * x j
          nlinarith [mul_nonneg (le_of_lt (hpos j)) (sub_nonneg.mpr (hxA j))])
        (hsx.mul_left A)
    have hQ : Tendsto (fun n => ∑ i ∈ range n, x i ^ 2) atTop (𝓝 S) := by
      rw [← hS]
      exact hsq.hasSum.tendsto_sum_nat
    have key : ∀ n, 2 ≤ n →
        2 * x 0 * x 1 ≤ (∑ i ∈ range n, x i) ^ 2 - ∑ i ∈ range n, x i ^ 2 := by
      intro n hn
      induction n, hn using Nat.le_induction with
      | base =>
        simp only [Finset.sum_range_succ, Finset.sum_range_zero, zero_add]
        nlinarith [sq_nonneg (x 0), sq_nonneg (x 1)]
      | succ m hm ih =>
        have hPm : 0 ≤ ∑ i ∈ range m, x i := Finset.sum_nonneg (fun i _ => le_of_lt (hpos i))
        simp only [Finset.sum_range_succ]
        nlinarith [ih, mul_nonneg hPm (le_of_lt (hpos m))]
    have hdiff : Tendsto (fun n => (∑ i ∈ range n, x i) ^ 2 - ∑ i ∈ range n, x i ^ 2)
        atTop (𝓝 (A ^ 2 - S)) :=
      (hP.pow 2).sub hQ
    have hlow : 2 * x 0 * x 1 ≤ A ^ 2 - S := by
      apply ge_of_tendsto hdiff
      filter_upwards [eventually_ge_atTop 2] with n hn
      exact key n hn
    have hSlow : x 0 ^ 2 ≤ S := by
      apply ge_of_tendsto hQ
      filter_upwards [eventually_ge_atTop 1] with n hn
      exact Finset.single_le_sum (f := fun i => x i ^ 2) (fun i _ => sq_nonneg (x i))
        (Finset.mem_range.mpr (by omega))
    have hpos0 : 0 < x 0 ^ 2 := pow_pos (hpos 0) 2
    constructor
    · linarith
    · nlinarith [mul_pos (hpos 0) (hpos 1)]
  · rintro ⟨hS0, hSA⟩
    obtain ⟨r, hr_def⟩ : ∃ r : ℝ, r = Real.sqrt S := ⟨_, rfl⟩
    have hr0 : 0 < r := by
      rw [hr_def]
      exact Real.sqrt_pos.mpr hS0
    have hr2 : r ^ 2 = S := by
      rw [hr_def]
      exact Real.sq_sqrt hS0.le
    have hrA : r < A := by nlinarith [mul_pos hr0 Apos]
    obtain ⟨a, ha_def⟩ : ∃ a : ℝ, a = r * (A - r) / (2 * A) := ⟨_, rfl⟩
    have ha2 : a * (2 * A) = r * (A - r) := by
      rw [ha_def]
      field_simp
    have hapos : 0 < a := by nlinarith [mul_pos hr0 (sub_pos.mpr hrA)]
    have hara : a < r := by nlinarith [mul_pos hr0 Apos, sq_nonneg r]
    have haAr : r < A - a := by
      nlinarith [mul_pos (sub_pos.mpr hrA) (show (0:ℝ) < 2 * A - r by linarith), ha2]
    have hd0 : 0 < A - a := by linarith
    have hAa0 : A - a ≠ 0 := hd0.ne'
    obtain ⟨t, ht_def⟩ : ∃ t : ℝ, t = (r ^ 2 - a ^ 2) / (A - a) ^ 2 := ⟨_, rfl⟩
    have htpos : 0 < t := by
      rw [ht_def]
      exact div_pos (by nlinarith [mul_pos (sub_pos.mpr hara) (add_pos hr0 hapos)]) (pow_pos hd0 2)
    have ht1 : t < 1 := by
      rw [ht_def, div_lt_one (pow_pos hd0 2)]
      nlinarith [mul_pos (sub_pos.mpr haAr) (show (0:ℝ) < A - a + r by linarith), sq_nonneg a]
    have htA : t * (A - a) ^ 2 = r ^ 2 - a ^ 2 := by
      rw [ht_def]
      field_simp
    obtain ⟨q, hq_def⟩ : ∃ q : ℝ, q = (1 - t) / (1 + t) := ⟨_, rfl⟩
    have h1t : (1 + t) ≠ 0 := (show (0:ℝ) < 1 + t by linarith).ne'
    have hq0 : 0 < q := by
      rw [hq_def]
      exact div_pos (by linarith) (by linarith)
    have hq1 : q < 1 := by
      rw [hq_def, div_lt_one (show (0:ℝ) < 1 + t by linarith)]
      linarith
    have hq_mul : q * (1 + t) = 1 - t := by
      rw [hq_def]
      field_simp
    have hqrel : 1 - q = t * (1 + q) := by
      linear_combination (-1:ℝ) * hq_mul
    have hq2 : q ^ 2 < 1 := by nlinarith
    have hq1ne : 1 - q ≠ 0 := (show (0:ℝ) < 1 - q by linarith).ne'
    obtain ⟨b, hb_def⟩ : ∃ b : ℝ, b = (A - a) * (1 - q) := ⟨_, rfl⟩
    have hb_pos : 0 < b := by
      rw [hb_def]
      exact mul_pos hd0 (by linarith)
    have hinv : b * (1 - q)⁻¹ = A - a := by
      rw [hb_def]
      exact mul_inv_cancel_right₀ hq1ne (A - a)
    have key2 : b ^ 2 * (1 - q ^ 2)⁻¹ = (A - a) ^ 2 * t := by
      have hd : 1 - q ^ 2 ≠ 0 := (show (0:ℝ) < 1 - q ^ 2 by nlinarith).ne'
      rw [← div_eq_mul_inv, div_eq_iff hd, hb_def]
      linear_combination ((A - a) ^ 2 * (1 - q)) * hqrel
    obtain ⟨x, hx⟩ : ∃ x : ℕ → ℝ, ∀ j, x j = if j = 0 then a else b * q ^ (j - 1) :=
      ⟨fun j => if j = 0 then a else b * q ^ (j - 1), fun j => rfl⟩
    have hx0 : x 0 = a := by
      rw [hx]
      simp
    have hxs : ∀ j, x (j + 1) = b * q ^ j := by
      intro j
      rw [hx]
      simp
    have hxpos : ∀ j, x j > 0 := by
      intro j
      rw [hx]
      split_ifs
      · exact hapos
      · exact mul_pos hb_pos (pow_pos hq0 _)
    have hsx : Summable x := by
      rw [← summable_nat_add_iff 1]
      exact ((summable_geometric_of_lt_one hq0.le hq1).mul_left b).congr
        (fun n => by simp [hxs])
    have hsq : Summable (fun j => x j ^ 2) := by
      rw [← summable_nat_add_iff 1]
      exact ((summable_geometric_of_lt_one (sq_nonneg q) hq2).mul_left (b ^ 2)).congr
        (fun n => by
          simp only [hxs]
          ring)
    have hsum1 : ∑' j, x j = A := by
      rw [hsx.tsum_eq_zero_add]
      simp only [hx0, hxs]
      rw [tsum_mul_left, tsum_geometric_of_lt_one hq0.le hq1, hinv]
      ring
    have hsum2 : ∑' j, x j ^ 2 = S := by
      rw [hsq.tsum_eq_zero_add]
      simp only [hx0, hxs]
      have hsq_eq : ∀ j : ℕ, (b * q ^ j) ^ 2 = b ^ 2 * (q ^ 2) ^ j := fun j => by ring
      simp only [hsq_eq]
      rw [tsum_mul_left, tsum_geometric_of_lt_one (sq_nonneg q) hq2, key2]
      linear_combination htA + hr2
    exact ⟨x, hxpos, hsum1, hsum2⟩

#print axioms putnam_2000_a1
