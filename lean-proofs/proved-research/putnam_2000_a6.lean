import Mathlib

open Finset

open Topology Filter

theorem putnam_2000_a6 (f : Polynomial ℤ)
(a : ℕ → ℤ)
(ha0 : a 0 = 0)
(ha : ∀ n : ℕ, a (n + 1) = f.eval (a n))
: ((∃ m > 0, a m = 0) → (a 1 = 0 ∨ a 2 = 0)) := by
  rintro ⟨m, hm, hma⟩
  by_cases h2 : a 2 = 0
  · exact Or.inr h2
  left
  by_contra h1
  have hm0 : m ≠ 0 := by omega
  -- the sequence is purely periodic with period m
  have hper : ∀ n, a (n + m) = a n := by
    intro n
    induction n with
    | zero => simp [ha0, hma]
    | succ k ih =>
      have e1 : a (k + 1 + m) = f.eval (a (k + m)) := by
        rw [show k + 1 + m = (k + m) + 1 by omega]
        exact ha (k + m)
      rw [e1, ih, ha k]
  have hmod : ∀ k n, a (k * m + n) = a n := by
    intro k
    induction k with
    | zero => intro n; simp
    | succ k ih =>
      intro n
      rw [show (k + 1) * m + n = (k * m + n) + m by ring, hper, ih]
  -- no two terms at distance 2 can coincide, otherwise a 2 = 0
  have hpair : ∀ i, a i ≠ a (i + 2) := by
    intro i hi
    have prop : ∀ t, a (i + t) = a (i + 2 + t) := by
      intro t
      induction t with
      | zero => simpa using hi
      | succ t ih =>
        have e1 : a (i + t + 1) = f.eval (a (i + t)) := ha (i + t)
        have e2 : a (i + 2 + t + 1) = f.eval (a (i + 2 + t)) := ha (i + 2 + t)
        have e3 : i + (t + 1) = i + t + 1 := by omega
        have e4 : i + 2 + (t + 1) = i + 2 + t + 1 := by omega
        rw [e3, e4, e1, e2, ih]
    have hle : i ≤ i * m := by
      first
      | exact Nat.le_mul_of_pos_right i hm
      | nlinarith
    obtain ⟨t, ht⟩ : ∃ t, i + t = i * m := ⟨i * m - i, by omega⟩
    have key := prop t
    rw [ht] at key
    have e6 : i + 2 + t = i * m + 2 := by omega
    rw [e6] at key
    have z1 : a (i * m) = a 0 := by
      have := hmod i 0
      rwa [add_zero] at this
    have z2 : a (i * m + 2) = a 2 := hmod i 2
    rw [z1, z2, ha0] at key
    exact h2 key.symm
  -- a_{k+1} - a_k divides a_{k+2} - a_{k+1}
  have hdvd : ∀ k, a (k + 1) - a k ∣ a (k + 1 + 1) - a (k + 1) := by
    intro k
    have e : a (k + 1 + 1) - a (k + 1) = f.eval (a (k + 1)) - f.eval (a k) := by
      rw [ha (k + 1), ← ha k]
    rw [e]
    exact Polynomial.sub_dvd_eval_sub _ _ _
  have hchain : ∀ k t, a (k + 1) - a k ∣ a (k + t + 1) - a (k + t) := by
    intro k t
    induction t with
    | zero => simp
    | succ t ih =>
      have e : k + (t + 1) = k + t + 1 := by omega
      rw [e]
      exact dvd_trans ih (hdvd (k + t))
  have hcyc : ∀ j, a (j * m + 1) - a (j * m) = a 1 - a 0 := by
    intro j
    have e1 := hmod j 1
    have e2 := hmod j 0
    rw [add_zero] at e2
    rw [e1, e2]
  -- every step divides the first step (using periodicity)
  have hD : ∀ k, a (k + 1) - a k ∣ a 1 - a 0 := by
    intro k
    have h := hchain k ((k + 1) * m - k)
    have hk : k + ((k + 1) * m - k) = (k + 1) * m := by
      have hle : k + 1 ≤ (k + 1) * m := by
        first
        | exact Nat.le_mul_of_pos_right (k + 1) hm
        | nlinarith
      first
      | omega
      | exact Nat.add_sub_of_le (le_trans (Nat.le_add_right k 1) hle)
    rw [hk] at h
    rw [hcyc (k + 1)] at h
    exact h
  have hD' : ∀ k, a 1 - a 0 ∣ a (k + 1) - a k := by
    intro k
    have h := hchain 0 k
    simp only [zero_add] at h
    exact h
  -- two elements dividing each other are equal or opposite
  have hab : ∀ x y : ℤ, x ∣ y → y ∣ x → y = x ∨ y = -x := by
    intro x y hxy hyx
    by_cases hx : x = 0
    · rw [hx] at hxy
      have hy : y = 0 := zero_dvd_iff.mp hxy
      left
      rw [hy, hx]
    · obtain ⟨u, hu⟩ := hxy
      obtain ⟨v, hv⟩ := hyx
      have h3 : x * (u * v) = x * 1 := by
        rw [hu] at hv
        linear_combination (-1 : ℤ) * hv
      have h4 : u * v = 1 := mul_left_cancel₀ hx h3
      have hu1 : u = 1 ∨ u = -1 := by
        first
        | exact Int.eq_one_or_neg_one_of_mul_eq_one h4
        | exact Int.isUnit_iff.mp (isUnit_of_mul_eq_one u v h4)
      rcases hu1 with h | h
      · left
        rw [hu, h, mul_one]
      · right
        rw [hu, h]
        ring
  -- every step is +a_1 or -a_1
  have hstep : ∀ k, a (k + 1) - a k = a 1 ∨ a (k + 1) - a k = -a 1 := by
    intro k
    have h := hab _ _ (hD' k) (hD k)
    rw [ha0, sub_zero] at h
    exact h
  -- main induction: a_k = k * a_1 for all k
  have hP : ∀ k : ℕ, a k = (k : ℤ) * a 1 ∧ a (k + 1) = ((k : ℤ) + 1) * a 1 := by
    intro k
    induction k with
    | zero => constructor <;> simp [ha0]
    | succ k ih =>
      obtain ⟨ih1, ih2⟩ := ih
      have hc : ((k + 1 : ℕ) : ℤ) = (k : ℤ) + 1 := Nat.cast_succ k
      rw [hc]
      refine ⟨ih2, ?_⟩
      rcases hstep (k + 1) with h | h
      · linear_combination h + ih2
      · exfalso
        have hh : a (k + 1 + 1) = a k := by
          linear_combination h + ih2 - ih1
        exact hpair k hh.symm
  have hfin := (hP m).1
  rw [hma] at hfin
  rcases mul_eq_zero.mp hfin.symm with h | h
  · exact hm0 (by exact_mod_cast h)
  · exact h1 h

#print axioms putnam_2000_a6
