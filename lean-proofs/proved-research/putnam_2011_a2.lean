import Mathlib

open Finset

open Topology Filter
noncomputable abbrev putnam_2011_a2_solution : ℝ := 3/2

theorem p2011a2_alg (P x bk S : ℝ) (hP : 0 < P) (hx : 0 < x)
    (h : bk / P = 1 - 2 * S) :
    (bk * x - 2) / (P * x) = 1 - 2 * (S + 1 / (P * x)) := by
  have hPne : P ≠ 0 := hP.ne'
  have hxne : x ≠ 0 := hx.ne'
  have hbk : bk = (1 - 2 * S) * P := by
    rw [div_eq_iff hPne] at h
    exact h
  rw [hbk]
  field_simp <;> ring

theorem p2011a2_pos (a : ℕ → ℝ) (ha : ∀ n : ℕ, 0 < a n) (n : ℕ) :
    0 < ∏ j ∈ range (n + 1), a j :=
  Finset.prod_pos (fun j _ => ha j)

theorem p2011a2_id (a b : ℕ → ℝ) (habn : ∀ n : ℕ, a n > 0 ∧ b n > 0)
    (hab1 : a 0 = 1 ∧ b 0 = 1)
    (hb : ∀ n ≥ 1, b n = b (n - 1) * a n - 2) (n : ℕ) :
    b n / ∏ j ∈ range (n + 1), a j
      = 1 - 2 * ∑ i ∈ range n, 1 / ∏ j ∈ range (i + 1 + 1), a j := by
  induction n with
  | zero => simp [Finset.prod_range_one, hab1.1, hab1.2]
  | succ k ih =>
    have hPpos : 0 < ∏ j ∈ range (k + 1), a j := p2011a2_pos a (fun j => (habn j).1) k
    have hapos : 0 < a (k + 1) := (habn (k + 1)).1
    have hP : ∏ j ∈ range (k + 1 + 1), a j = (∏ j ∈ range (k + 1), a j) * a (k + 1) :=
      Finset.prod_range_succ _ _
    have hb' : b (k + 1) = b k * a (k + 1) - 2 := by
      have h := hb (k + 1) (by omega)
      simpa using h
    rw [Finset.sum_range_succ, hP, hb']
    exact p2011a2_alg _ _ _ _ hPpos hapos ih

theorem p2011a2_key (a b : ℕ → ℝ) (habn : ∀ n : ℕ, a n > 0 ∧ b n > 0)
    (hb : ∀ n ≥ 1, b n = b (n - 1) * a n - 2) (n : ℕ) :
    (b (n + 1) / ∏ j ∈ range (n + 1 + 1), a j) * (b (n + 1) + 2)
      = (b n / ∏ j ∈ range (n + 1), a j) * b (n + 1) := by
  have hPpos : 0 < ∏ j ∈ range (n + 1), a j := p2011a2_pos a (fun j => (habn j).1) n
  have hapos : 0 < a (n + 1) := (habn (n + 1)).1
  have hP : ∏ j ∈ range (n + 1 + 1), a j = (∏ j ∈ range (n + 1), a j) * a (n + 1) :=
    Finset.prod_range_succ _ _
  have hb' : b (n + 1) = b n * a (n + 1) - 2 := by
    have h := hb (n + 1) (by omega)
    simpa using h
  have h2 : b (n + 1) + 2 = b n * a (n + 1) := by
    rw [hb']
    ring
  have hPne : (∏ j ∈ range (n + 1), a j) ≠ 0 := hPpos.ne'
  have hane : a (n + 1) ≠ 0 := hapos.ne'
  rw [hP, h2]
  field_simp <;> ring

theorem p2011a2_step (c0 c1 b1 B : ℝ) (hc0 : 0 < c0) (hc1 : 0 < c1) (hb1 : 0 < b1)
    (hbB : b1 ≤ B) (key : c1 * (b1 + 2) = c0 * b1) :
    c1 ≤ B / (B + 2) * c0 := by
  have hB2 : 0 < B + 2 := by linarith
  have key2 : B * (c1 * (b1 + 2)) = B * (c0 * b1) := by rw [key]
  rw [div_mul_eq_mul_div, le_div_iff₀ hB2]
  nlinarith [key2, mul_nonneg hc1.le (sub_nonneg.2 hbB), hb1]

theorem p2011a2_bound (a b : ℕ → ℝ) (habn : ∀ n : ℕ, a n > 0 ∧ b n > 0)
    (hab1 : a 0 = 1 ∧ b 0 = 1)
    (hb : ∀ n ≥ 1, b n = b (n - 1) * a n - 2) (B : ℝ) (hB : ∀ n : ℕ, b n ≤ B)
    (n : ℕ) :
    b n / ∏ j ∈ range (n + 1), a j ≤ (B / (B + 2)) ^ n := by
  have hB1 : 1 ≤ B := by linarith [hB 0, hab1.2]
  have hq0 : 0 ≤ B / (B + 2) := div_nonneg (by linarith) (by linarith)
  induction n with
  | zero => simp [Finset.prod_range_one, hab1.1, hab1.2]
  | succ k ih =>
    have hc0 : 0 < b k / ∏ j ∈ range (k + 1), a j :=
      div_pos (habn k).2 (p2011a2_pos a (fun j => (habn j).1) k)
    have hc1 : 0 < b (k + 1) / ∏ j ∈ range (k + 1 + 1), a j :=
      div_pos (habn (k + 1)).2 (p2011a2_pos a (fun j => (habn j).1) (k + 1))
    have hstep := p2011a2_step _ _ _ B hc0 hc1 (habn (k + 1)).2 (hB (k + 1))
      (p2011a2_key a b habn hb k)
    have hpow : (B / (B + 2)) ^ (k + 1) = B / (B + 2) * (B / (B + 2)) ^ k := by ring
    rw [hpow]
    exact le_trans hstep (mul_le_mul_of_nonneg_left ih hq0)

theorem putnam_2011_a2 (a b : ℕ → ℝ)
(habn : ∀ n : ℕ, a n > 0 ∧ b n > 0)
(hab1 : a 0 = 1 ∧ b 0 = 1)
(hb : ∀ n ≥ 1, b n = b (n-1) * a n - 2)
(hbnd : ∃ B : ℝ, ∀ n : ℕ, |b n| ≤ B)
: Tendsto (fun n => ∑ i : Fin n, 1/(∏ j : Fin (i + 1), (a j))) atTop (𝓝 putnam_2011_a2_solution) := by
  obtain ⟨B, hB⟩ := hbnd
  have hBb : ∀ n : ℕ, b n ≤ B := fun n => le_trans (le_abs_self _) (hB n)
  have hB1 : 1 ≤ B := by linarith [hBb 0, hab1.2]
  have hq0 : 0 ≤ B / (B + 2) := div_nonneg (by linarith) (by linarith)
  have hq1 : B / (B + 2) < 1 :=
    (div_lt_one (show (0:ℝ) < B + 2 by linarith)).2 (by linarith)
  have ha : ∀ n : ℕ, 0 < a n := fun n => (habn n).1
  have hc : Tendsto (fun n => b n / ∏ j ∈ range (n + 1), a j) atTop (𝓝 0) := by
    refine squeeze_zero (fun n => (div_pos (habn n).2 (p2011a2_pos a ha n)).le)
      (fun n => p2011a2_bound a b habn hab1 hb B hBb n) ?_
    exact tendsto_pow_atTop_nhds_zero_of_lt_one hq0 hq1
  have hG : ∀ n : ℕ, ∑ i ∈ range (n + 1), 1 / ∏ j ∈ range (i + 1), a j
      = 3 / 2 - (b n / ∏ j ∈ range (n + 1), a j) / 2 := by
    intro n
    have h := p2011a2_id a b habn hab1 hb n
    have h0 : (1:ℝ) / ∏ j ∈ range (0 + 1), a j = 1 := by simp [hab1.1]
    rw [Finset.sum_range_succ']
    linarith [h, h0]
  have hlim1 : Tendsto (fun n => (3 / 2 : ℝ) - (b n / ∏ j ∈ range (n + 1), a j) / 2)
      atTop (𝓝 (3 / 2)) := by
    have := (tendsto_const_nhds : Tendsto (fun _ : ℕ => (3 / 2 : ℝ)) atTop (𝓝 (3 / 2))).sub
      (hc.div_const 2)
    rw [show ((3 : ℝ) / 2 - 0 / 2) = 3 / 2 by norm_num] at this
    exact this
  have hlim2 : Tendsto (fun n => ∑ i ∈ range (n + 1), 1 / ∏ j ∈ range (i + 1), a j)
      atTop (𝓝 (3 / 2)) :=
    Tendsto.congr (fun n => (hG n).symm) hlim1
  have hlim3 : Tendsto (fun n => ∑ i ∈ range n, 1 / ∏ j ∈ range (i + 1), a j)
      atTop (𝓝 (3 / 2)) :=
    (Filter.tendsto_add_atTop_iff_nat
      (f := fun m : ℕ => ∑ i ∈ range m, 1 / ∏ j ∈ range (i + 1), a j) 1).1 hlim2
  have hconv : ∀ n : ℕ, ∑ i : Fin n, 1 / (∏ j : Fin (i + 1), (a j))
      = ∑ i ∈ range n, 1 / ∏ j ∈ range (i + 1), a j := by
    intro n
    have h1 : ∀ i : Fin n, 1 / (∏ j : Fin (i + 1), (a j))
        = (fun m : ℕ => 1 / ∏ j ∈ range (m + 1), a j) (i : ℕ) := by
      intro i
      rw [Fin.prod_univ_eq_prod_range a ((i : ℕ) + 1)]
    exact (Finset.sum_congr rfl (fun i _ => h1 i)).trans
      (Fin.sum_univ_eq_sum_range (fun m : ℕ => 1 / ∏ j ∈ range (m + 1), a j) n)
  exact Tendsto.congr (fun n => (hconv n).symm) hlim3

#print axioms putnam_2011_a2
