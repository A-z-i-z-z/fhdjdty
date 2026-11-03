import Mathlib

open Finset

open Filter Topology
noncomputable abbrev putnam_2021_b2_solution : ℝ := 2 / 3

lemma gauss_Icc (n : ℕ) : 2 * ∑ k ∈ Finset.Icc 1 n, k = n * (n + 1) := by
  induction n with
  | zero => simp
  | succ m ih =>
    rw [Finset.sum_Icc_succ_top (by omega)]
    nlinarith [ih]

lemma pointwise_bound (a : ℕ → ℝ) (ha : ∀ k, 0 ≤ a k) (n : ℕ) (hn : 1 ≤ n) :
    (n : ℝ) * (∏ k ∈ Finset.Icc 1 n, a k) ^ (1 / (n : ℝ)) ≤
      (1 / 2) ^ (n + 1) * ∑ k ∈ Finset.Icc 1 n, a k * 4 ^ k := by
  have hnpos : (0 : ℝ) < n := by exact_mod_cast hn
  have hn0 : (n : ℝ) ≠ 0 := hnpos.ne'
  have hGnn : 0 ≤ ∏ k ∈ Finset.Icc 1 n, a k := Finset.prod_nonneg (fun k _ => ha k)
  have hamgm : ∏ i ∈ Finset.Icc 1 n, (a i * 4 ^ i) ^ (1 / (n : ℝ)) ≤
      ∑ i ∈ Finset.Icc 1 n, 1 / (n : ℝ) * (a i * 4 ^ i) :=
    Real.geom_mean_le_arith_mean_weighted (Finset.Icc 1 n)
      (fun _ => 1 / (n : ℝ)) (fun k => a k * 4 ^ k)
      (fun _ _ => div_nonneg zero_le_one hnpos.le)
      (by simp only [Finset.sum_const, Nat.card_Icc, nsmul_eq_mul, Nat.add_sub_cancel]; field_simp)
      (fun k _ => mul_nonneg (ha k) (by positivity))
  have hprod : ∏ i ∈ Finset.Icc 1 n, (a i * 4 ^ i) =
      (∏ i ∈ Finset.Icc 1 n, a i) * 2 ^ (n * (n + 1)) := by
    rw [Finset.prod_mul_distrib, Finset.prod_pow_eq_pow_sum,
      show (4 : ℝ) = 2 ^ 2 by norm_num, ← pow_mul, gauss_Icc n]
  have hX : ((2 : ℝ) ^ (n * (n + 1))) ^ (1 / (n : ℝ)) = 2 ^ (n + 1) := by
    rw [← Real.rpow_natCast, ← Real.rpow_natCast, ← Real.rpow_mul (by norm_num : (0:ℝ) ≤ 2)]
    congr 1
    rw [mul_one_div, div_eq_iff hn0]
    push_cast
    ring
  have hamgm2 : (∏ i ∈ Finset.Icc 1 n, a i) ^ (1 / (n : ℝ)) * 2 ^ (n + 1) ≤
      1 / (n : ℝ) * ∑ i ∈ Finset.Icc 1 n, a i * 4 ^ i := by
    have h := hamgm
    rw [Real.finset_prod_rpow (Finset.Icc 1 n) (fun i => a i * 4 ^ i)
      (fun i _ => mul_nonneg (ha i) (by positivity)), hprod,
      Real.mul_rpow hGnn (by positivity : (0:ℝ) ≤ 2 ^ (n * (n + 1))), hX, ← Finset.mul_sum] at h
    exact h
  have key : (n : ℝ) * ((∏ i ∈ Finset.Icc 1 n, a i) ^ (1 / (n : ℝ)) * 2 ^ (n + 1)) ≤
      ∑ i ∈ Finset.Icc 1 n, a i * 4 ^ i := by
    have h2 := mul_le_mul_of_nonneg_left hamgm2 hnpos.le
    have e : (n : ℝ) * (1 / (n : ℝ) * ∑ i ∈ Finset.Icc 1 n, a i * 4 ^ i) =
        ∑ i ∈ Finset.Icc 1 n, a i * 4 ^ i := by
      rw [← mul_assoc, mul_one_div_cancel hn0, one_mul]
    linarith
  have hinv : (1 / 2 : ℝ) ^ (n + 1) * 2 ^ (n + 1) = 1 := by
    rw [← mul_pow]; norm_num
  calc (n : ℝ) * (∏ i ∈ Finset.Icc 1 n, a i) ^ (1 / (n : ℝ))
      = (1 / 2) ^ (n + 1) * ((n : ℝ) * ((∏ i ∈ Finset.Icc 1 n, a i) ^ (1 / (n : ℝ)) * 2 ^ (n + 1))) := by
        linear_combination (-((n : ℝ) * (∏ i ∈ Finset.Icc 1 n, a i) ^ (1 / (n : ℝ)))) * hinv
    _ ≤ (1 / 2) ^ (n + 1) * ∑ i ∈ Finset.Icc 1 n, a i * 4 ^ i :=
        mul_le_mul_of_nonneg_left key (by positivity)

lemma term_bound (a : ℕ → ℝ) (ha : ∀ k, 0 ≤ a k) (n : ℕ) (hn : 1 ≤ n) :
    (n : ℝ) / 2 ^ n * (∏ k ∈ Finset.Icc 1 n, a k) ^ (1 / (n : ℝ)) ≤
      (1 / 4) ^ n * (1 / 2) * ∑ k ∈ Finset.Icc 1 n, a k * 4 ^ k := by
  have h1 := pointwise_bound a ha n hn
  have e1 : (n : ℝ) / 2 ^ n * (∏ k ∈ Finset.Icc 1 n, a k) ^ (1 / (n : ℝ)) =
      (1 / 2) ^ n * ((n : ℝ) * (∏ k ∈ Finset.Icc 1 n, a k) ^ (1 / (n : ℝ))) := by
    rw [one_div_pow]; ring
  have e2 : (1 / 2 : ℝ) ^ n * (1 / 2) ^ (n + 1) = (1 / 4) ^ n * (1 / 2) := by
    rw [pow_succ, ← mul_assoc, ← mul_pow]; norm_num
  rw [e1]
  calc (1 / 2 : ℝ) ^ n * ((n : ℝ) * (∏ k ∈ Finset.Icc 1 n, a k) ^ (1 / (n : ℝ)))
      ≤ (1 / 2) ^ n * ((1 / 2) ^ (n + 1) * ∑ k ∈ Finset.Icc 1 n, a k * 4 ^ k) :=
        mul_le_mul_of_nonneg_left h1 (by positivity)
    _ = (1 / 4) ^ n * (1 / 2) * ∑ k ∈ Finset.Icc 1 n, a k * 4 ^ k := by
        rw [← e2]; ring

lemma tele (a : ℕ → ℝ) (N : ℕ) :
    ∑ n ∈ Finset.Icc 1 N, (1 / 4 : ℝ) ^ n * (1 / 2) * ∑ k ∈ Finset.Icc 1 n, a k * 4 ^ k
      + (2 / 3 : ℝ) * (1 / 4) ^ (N + 1) * ∑ k ∈ Finset.Icc 1 N, a k * 4 ^ k
      = (2 / 3 : ℝ) * ∑ k ∈ Finset.Icc 1 N, a k := by
  induction N with
  | zero => simp
  | succ m ih =>
    have hR : ∑ n ∈ Finset.Icc 1 (m + 1), (1 / 4 : ℝ) ^ n * (1 / 2) * ∑ k ∈ Finset.Icc 1 n, a k * 4 ^ k
        = ∑ n ∈ Finset.Icc 1 m, (1 / 4 : ℝ) ^ n * (1 / 2) * ∑ k ∈ Finset.Icc 1 n, a k * 4 ^ k
          + (1 / 4 : ℝ) ^ (m + 1) * (1 / 2) * ∑ k ∈ Finset.Icc 1 (m + 1), a k * 4 ^ k :=
      Finset.sum_Icc_succ_top (by omega) (fun n => (1 / 4 : ℝ) ^ n * (1 / 2) * ∑ k ∈ Finset.Icc 1 n, a k * 4 ^ k)
    have hT : ∑ k ∈ Finset.Icc 1 (m + 1), a k * 4 ^ k
        = ∑ k ∈ Finset.Icc 1 m, a k * 4 ^ k + a (m + 1) * 4 ^ (m + 1) :=
      Finset.sum_Icc_succ_top (by omega) (fun k => a k * 4 ^ k)
    have hA : ∑ k ∈ Finset.Icc 1 (m + 1), a k = ∑ k ∈ Finset.Icc 1 m, a k + a (m + 1) :=
      Finset.sum_Icc_succ_top (by omega) (fun k => a k)
    have hp : (1 / 4 : ℝ) ^ (m + 1) * 4 ^ (m + 1) = 1 := by
      rw [← mul_pow]; norm_num
    have hq : (1 / 4 : ℝ) ^ (m + 1 + 1) = (1 / 4) ^ (m + 1) * (1 / 4) := pow_succ _ _
    rw [hR, hT, hA]
    linear_combination ih
      + (2 / 3 * a (m + 1)) * hp
      + (2 / 3 * (∑ k ∈ Finset.Icc 1 m, a k * 4 ^ k + a (m + 1) * 4 ^ (m + 1))) * hq

lemma range_Icc (g : ℕ → ℝ) (K : ℕ) :
    ∑ m ∈ Finset.range K, g (m + 1) = ∑ n ∈ Finset.Icc 1 K, g n := by
  induction K with
  | zero => simp
  | succ K ih => rw [Finset.sum_range_succ, Finset.sum_Icc_succ_top (by omega), ih]

lemma sum_pnat_Icc (f : ℕ+ → ℝ) (n : ℕ+) :
    ∑ k ∈ Finset.Icc 1 n, f k = ∑ j ∈ Finset.Icc 1 (n : ℕ), f (Nat.succPNat (j - 1)) := by
  apply Finset.sum_nbij' (fun k : ℕ+ => (k : ℕ)) (fun j : ℕ => Nat.succPNat (j - 1))
  · intro k hk
    rw [Finset.mem_Icc] at hk
    rw [Finset.mem_Icc]
    exact ⟨k.pos, (PNat.coe_le_coe _ _).2 hk.2⟩
  · intro j hj
    rw [Finset.mem_Icc] at hj
    rw [Finset.mem_Icc]
    refine ⟨PNat.one_le _, ?_⟩
    rw [← PNat.coe_le_coe, Nat.succPNat_coe]
    omega
  · intro k _
    apply PNat.eq
    show ((k : ℕ) - 1).succ = (k : ℕ)
    have := k.pos
    omega
  · intro j hj
    rw [Finset.mem_Icc] at hj
    show (j - 1).succ = j
    omega
  · intro k _
    show f k = f (Nat.succPNat ((k : ℕ) - 1))
    congr 1
    apply PNat.eq
    show (k : ℕ) = ((k : ℕ) - 1).succ
    have := k.pos
    omega

lemma prod_pnat_Icc (f : ℕ+ → ℝ) (n : ℕ+) :
    ∏ k ∈ Finset.Icc 1 n, f k = ∏ j ∈ Finset.Icc 1 (n : ℕ), f (Nat.succPNat (j - 1)) := by
  apply Finset.prod_nbij' (fun k : ℕ+ => (k : ℕ)) (fun j : ℕ => Nat.succPNat (j - 1))
  · intro k hk
    rw [Finset.mem_Icc] at hk
    rw [Finset.mem_Icc]
    exact ⟨k.pos, (PNat.coe_le_coe _ _).2 hk.2⟩
  · intro j hj
    rw [Finset.mem_Icc] at hj
    rw [Finset.mem_Icc]
    refine ⟨PNat.one_le _, ?_⟩
    rw [← PNat.coe_le_coe, Nat.succPNat_coe]
    omega
  · intro k _
    apply PNat.eq
    show ((k : ℕ) - 1).succ = (k : ℕ)
    have := k.pos
    omega
  · intro j hj
    rw [Finset.mem_Icc] at hj
    show (j - 1).succ = j
    omega
  · intro k _
    show f k = f (Nat.succPNat ((k : ℕ) - 1))
    congr 1
    apply PNat.eq
    show (k : ℕ) = ((k : ℕ) - 1).succ
    have := k.pos
    omega

lemma tsum_pnat_eq (f : ℕ+ → ℝ) : ∑' n : ℕ+, f n = ∑' m : ℕ, f m.succPNat :=
  (Equiv.tsum_eq Equiv.pnatEquivNat.symm f).symm

lemma pnat_term (a : ℕ+ → ℝ) (ha : ∀ k, 0 ≤ a k) (m : ℕ) :
    ((m.succPNat : ℕ) : ℝ) / 2 ^ (m.succPNat : ℕ) *
      (∏ k ∈ Finset.Icc 1 m.succPNat, a k) ^ (1 / ((m.succPNat : ℕ) : ℝ)) ≤
      (1 / 4 : ℝ) ^ (m + 1) * (1 / 2) * ∑ j ∈ Finset.Icc 1 (m + 1), a (Nat.succPNat (j - 1)) * 4 ^ j := by
  have hc : (m.succPNat : ℕ) = m + 1 := rfl
  rw [prod_pnat_Icc a m.succPNat, hc]
  exact term_bound (fun j => a (Nat.succPNat (j - 1))) (fun j => ha _) (m + 1) (by omega)

lemma sum_range_bound (a : ℕ+ → ℝ) (hnn : ∀ k, 0 ≤ a k) (hsa : Summable a)
    (hsum : ∑' k, a k = 1) (K : ℕ) :
    ∑ m ∈ Finset.range K, ((m.succPNat : ℕ) : ℝ) / 2 ^ (m.succPNat : ℕ) *
      (∏ k ∈ Finset.Icc 1 m.succPNat, a k) ^ (1 / ((m.succPNat : ℕ) : ℝ)) ≤ 2 / 3 := by
  refine le_trans (Finset.sum_le_sum (fun m _ => pnat_term a hnn m)) ?_
  rw [range_Icc (fun n => (1 / 4 : ℝ) ^ n * (1 / 2) *
    ∑ j ∈ Finset.Icc 1 n, a (Nat.succPNat (j - 1)) * 4 ^ j) K]
  have ht := tele (fun j => a (Nat.succPNat (j - 1))) K
  beta_reduce at ht
  have hT : 0 ≤ ∑ j ∈ Finset.Icc 1 K, a (Nat.succPNat (j - 1)) * 4 ^ j :=
    Finset.sum_nonneg (fun j _ => mul_nonneg (hnn _) (by positivity))
  have hq : 0 ≤ (2 / 3 : ℝ) * (1 / 4) ^ (K + 1) *
      ∑ j ∈ Finset.Icc 1 K, a (Nat.succPNat (j - 1)) * 4 ^ j :=
    mul_nonneg (by positivity) hT
  have hAK : ∑ j ∈ Finset.Icc 1 K, a (Nat.succPNat (j - 1)) ≤ 1 := by
    have h1 : ∑ j ∈ Finset.Icc 1 K, a (Nat.succPNat (j - 1)) ≤
        ∑ j ∈ Finset.Icc 1 (K + 1), a (Nat.succPNat (j - 1)) :=
      Finset.sum_le_sum_of_subset_of_nonneg (Finset.Icc_subset_Icc le_rfl (by omega))
        (fun j _ _ => hnn _)
    have h2 : ∑ j ∈ Finset.Icc 1 (K + 1), a (Nat.succPNat (j - 1)) =
        ∑ k ∈ Finset.Icc (1 : ℕ+) (Nat.succPNat K), a k :=
      (sum_pnat_Icc a (Nat.succPNat K)).symm
    have h3 : ∑ k ∈ Finset.Icc (1 : ℕ+) (Nat.succPNat K), a k ≤ 1 :=
      (Summable.sum_le_tsum _ (fun k _ => hnn k) hsa).trans_eq hsum
    linarith
  linarith

lemma prod_geom_nat (m : ℕ) :
    ∏ j ∈ Finset.Icc 1 m, (3 * (1 / 4 : ℝ) ^ j) =
      3 ^ m * (1 / 4 : ℝ) ^ (∑ j ∈ Finset.Icc 1 m, j) := by
  rw [Finset.prod_mul_distrib, Finset.prod_const, Finset.prod_pow_eq_pow_sum,
    Nat.card_Icc, Nat.add_sub_cancel]

lemma member_nat (m : ℕ) (hm : 1 ≤ m) :
    (m : ℝ) / 2 ^ m * (∏ j ∈ Finset.Icc 1 m, 3 * (1 / 4 : ℝ) ^ j) ^ (1 / (m : ℝ)) =
      3 / 2 * (m : ℝ) * (1 / 4 : ℝ) ^ m := by
  have hmpos : (0 : ℝ) < m := by exact_mod_cast hm
  have hm0 : (m : ℝ) ≠ 0 := hmpos.ne'
  have hS : (1 / 4 : ℝ) ^ (∑ j ∈ Finset.Icc 1 m, j) = (1 / 2) ^ (m * (m + 1)) := by
    rw [← gauss_Icc m, pow_mul]; norm_num
  have h3 : ((3 : ℝ) ^ m) ^ (1 / (m : ℝ)) = 3 := by
    rw [← Real.rpow_natCast, ← Real.rpow_mul (by norm_num : (0:ℝ) ≤ 3), mul_one_div_cancel hm0,
      Real.rpow_one]
  have h2 : ((1 / 2 : ℝ) ^ (m * (m + 1))) ^ (1 / (m : ℝ)) = (1 / 2) ^ (m + 1) := by
    rw [← Real.rpow_natCast, ← Real.rpow_natCast,
      ← Real.rpow_mul (by norm_num : (0:ℝ) ≤ 1 / 2)]
    congr 1
    rw [mul_one_div, div_eq_iff hm0]
    push_cast
    ring
  have h4 : (1 / 4 : ℝ) ^ m = (1 / 2) ^ m * (1 / 2) ^ m := by
    rw [← mul_pow]; norm_num
  have h6 : (m : ℝ) / 2 ^ m = m * (1 / 2) ^ m := by
    rw [one_div_pow]; ring
  rw [prod_geom_nat m, hS,
    Real.mul_rpow (by positivity : (0:ℝ) ≤ 3 ^ m) (by positivity : (0:ℝ) ≤ (1 / 2 : ℝ) ^ (m * (m + 1))),
    h3, h2, h6, pow_succ, h4]
  ring

lemma member_pnat (n : ℕ+) :
    ((n : ℕ) : ℝ) / 2 ^ (n : ℕ) *
      (∏ k ∈ Finset.Icc 1 n, 3 * (1 / 4 : ℝ) ^ (k : ℕ)) ^ (1 / ((n : ℕ) : ℝ)) =
      3 / 2 * (((n : ℕ) : ℝ) * (1 / 4 : ℝ) ^ (n : ℕ)) := by
  rw [prod_pnat_Icc (fun k : ℕ+ => 3 * (1 / 4 : ℝ) ^ (k : ℕ)) n]
  have hc : ∀ j ∈ Finset.Icc 1 (n : ℕ),
      (fun k : ℕ+ => 3 * (1 / 4 : ℝ) ^ (k : ℕ)) (Nat.succPNat (j - 1)) = 3 * (1 / 4 : ℝ) ^ j := by
    intro j hj
    rw [Finset.mem_Icc] at hj
    have hjj : ((Nat.succPNat (j - 1) : ℕ+) : ℕ) = j := by
      show (j - 1).succ = j
      omega
    show 3 * (1 / 4 : ℝ) ^ ((Nat.succPNat (j - 1) : ℕ+) : ℕ) = 3 * (1 / 4 : ℝ) ^ j
    rw [hjj]
  rw [Finset.prod_congr rfl hc]
  have := member_nat (n : ℕ) n.pos
  linarith

lemma pnat_coe_geom : ∑' n : ℕ+, ((n : ℕ) : ℝ) * (1 / 4 : ℝ) ^ (n : ℕ) = 4 / 9 := by
  rw [tsum_pnat_eq]
  have h0 : HasSum (fun m : ℕ => (m : ℝ) * (1 / 4 : ℝ) ^ m) ((1 / 4 : ℝ) / (1 - 1 / 4) ^ 2) :=
    hasSum_coe_mul_geometric_of_norm_lt_one (by rw [Real.norm_eq_abs]; norm_num)
  have h1 := (hasSum_nat_add_iff' 1).mpr h0
  have h2 : ∑' m : ℕ, ((m.succPNat : ℕ) : ℝ) * (1 / 4 : ℝ) ^ (m.succPNat : ℕ) =
      (1 / 4 : ℝ) / (1 - 1 / 4) ^ 2 - ∑ i ∈ Finset.range 1, (i : ℝ) * (1 / 4 : ℝ) ^ i :=
    h1.tsum_eq
  rw [h2]
  norm_num [Finset.sum_range_one]

lemma pnat_geom_sum : ∑' k : ℕ+, (3 : ℝ) * (1 / 4 : ℝ) ^ (k : ℕ) = 1 := by
  rw [tsum_pnat_eq]
  have h0 := (hasSum_geometric_of_lt_one (by norm_num : (0:ℝ) ≤ 1 / 4)
    (by norm_num : (1 / 4 : ℝ) < 1)).mul_left 3
  have h1 := (hasSum_nat_add_iff' 1).mpr h0
  have h2 : ∑' m : ℕ, (3 : ℝ) * (1 / 4 : ℝ) ^ (m.succPNat : ℕ) =
      3 * (1 - 1 / 4)⁻¹ - ∑ i ∈ Finset.range 1, (3 : ℝ) * (1 / 4 : ℝ) ^ i :=
    h1.tsum_eq
  rw [h2]
  norm_num [Finset.sum_range_one]

theorem putnam_2021_b2 :
    IsGreatest
      {S | ∃ a : ℕ+ → ℝ, (∑' k, a k = 1) ∧ (∀ k, 0 ≤ a k) ∧
        S = ∑' n : ℕ+, n / 2 ^ (n : ℕ) * (∏ k ∈ Finset.Icc 1 n, a k) ^ (1 / n : ℝ)}
      putnam_2021_b2_solution := by
  unfold IsGreatest upperBounds
  refine ⟨?_, ?_⟩
  · rw [Set.mem_setOf_eq]
    refine ⟨fun k => 3 * (1 / 4 : ℝ) ^ (k : ℕ), ?_, ?_, ?_⟩
    · exact pnat_geom_sum
    · intro k
      show (0 : ℝ) ≤ 3 * (1 / 4 : ℝ) ^ (k : ℕ)
      positivity
    · have hfun : ∀ n : ℕ+,
          ((n : ℕ) : ℝ) / 2 ^ (n : ℕ) *
            (∏ k ∈ Finset.Icc 1 n, 3 * (1 / 4 : ℝ) ^ (k : ℕ)) ^ (1 / ((n : ℕ) : ℝ)) =
          3 / 2 * (((n : ℕ) : ℝ) * (1 / 4 : ℝ) ^ (n : ℕ)) :=
        fun n => member_pnat n
      show (2 / 3 : ℝ) = _
      simp only [hfun]
      rw [tsum_mul_left, pnat_coe_geom]
      norm_num
  · intro S hS
    obtain ⟨a, hsum, hnn, rfl⟩ := hS
    show _ ≤ (2 / 3 : ℝ)
    have hsa : Summable a := by
      by_contra h
      rw [tsum_eq_zero_of_not_summable h] at hsum
      norm_num at hsum
    rw [tsum_pnat_eq]
    apply tsum_le_of_sum_le' (by norm_num : (0:ℝ) ≤ 2 / 3)
    intro s
    have hsub : s ⊆ Finset.range (s.sup id + 1) := by
      intro m hm
      rw [Finset.mem_range]
      have := Finset.le_sup (f := id) hm
      simp only [id_eq] at this
      omega
    refine le_trans (Finset.sum_le_sum_of_subset_of_nonneg hsub (fun m _ _ => ?_))
      (sum_range_bound a hnn hsa hsum _)
    exact mul_nonneg (by positivity)
      (Real.rpow_nonneg (Finset.prod_nonneg fun k _ => hnn k) _)

#print axioms putnam_2021_b2
