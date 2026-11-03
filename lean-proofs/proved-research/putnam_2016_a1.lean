import Mathlib

open Finset

open Polynomial Filter Topology Real Set Nat
abbrev putnam_2016_a1_solution : ℕ := 8

theorem putnam_2016_a1_iter_add (n : ℕ) (p q : ℤ[X]) :
    derivative^[n] (p + q) = derivative^[n] p + derivative^[n] q := by
  induction n generalizing p q with
  | zero => simp
  | succ m ih =>
    simp only [Function.iterate_succ_apply]
    rw [Polynomial.derivative_add]
    exact ih _ _

theorem putnam_2016_a1_iter_Cmul (j : ℕ) (a : ℤ) (p : ℤ[X]) :
    derivative^[j] (C a * p) = C a * derivative^[j] p := by
  induction j generalizing p with
  | zero => simp
  | succ m ih =>
    simp only [Function.iterate_succ_apply]
    first
    | rw [Polynomial.derivative_C_mul]
    | simp [Polynomial.derivative_mul]
    exact ih _

theorem putnam_2016_a1_mono (n : ℕ) (a k : ℤ) :
    (40320 : ℤ) ∣ (derivative^[8] (C a * X ^ n)).eval k := by
  have h8 : (8 : ℕ)! = 40320 := by norm_num [Nat.factorial]
  have hd : (40320 : ℤ) ∣ ((n.descFactorial 8 : ℕ) : ℤ) := by
    have h1 : (40320 : ℕ) ∣ n.descFactorial 8 := by
      rw [← h8]
      exact Nat.factorial_dvd_descFactorial n 8
    exact_mod_cast h1
  have key : (derivative^[8] (X ^ n : ℤ[X])).eval k
      = ((n.descFactorial 8 : ℕ) : ℤ) * k ^ (n - 8) := by
    simp [Polynomial.iterate_derivative_X_pow_eq_smul]
  rw [putnam_2016_a1_iter_Cmul, Polynomial.eval_mul, Polynomial.eval_C, key]
  first
  | exact Dvd.dvd.mul_left (Dvd.dvd.mul_right hd _) _
  | exact dvd_mul_of_dvd_right (dvd_mul_of_dvd_left hd _) _
  | (apply Dvd.dvd.mul_left; apply Dvd.dvd.mul_right; exact hd)

theorem putnam_2016_a1_all (P : ℤ[X]) (k : ℤ) :
    (40320 : ℤ) ∣ (derivative^[8] P).eval k := by
  induction P using Polynomial.induction_on' with
  | add p q hp hq =>
    rw [putnam_2016_a1_iter_add, Polynomial.eval_add]
    exact dvd_add hp hq
  | monomial n a =>
    rw [← Polynomial.C_mul_X_pow_eq_monomial]
    exact putnam_2016_a1_mono n a k

theorem putnam_2016_a1 :
  IsLeast {j : ℕ | 0 < j ∧ ∀ P : ℤ[X], ∀ k : ℤ, 2016 ∣ (derivative^[j] P).eval k} putnam_2016_a1_solution := by
  constructor
  · show 0 < 8 ∧ ∀ P : ℤ[X], ∀ k : ℤ, 2016 ∣ (derivative^[8] P).eval k
    refine ⟨by norm_num, fun P k => ?_⟩
    exact dvd_trans (by norm_num : (2016 : ℤ) ∣ 40320) (putnam_2016_a1_all P k)
  · intro j hj
    obtain ⟨hjpos, hall⟩ := hj
    show 8 ≤ j
    by_contra hlt
    push_neg at hlt
    have h := hall (X ^ j) 1
    have key : (derivative^[j] (X ^ j : ℤ[X])).eval 1 = ((j ! : ℕ) : ℤ) := by
      simp [Polynomial.iterate_derivative_X_pow_eq_smul, Nat.descFactorial_self]
    rw [key] at h
    interval_cases j <;> norm_num [Nat.factorial] at h

#print axioms putnam_2016_a1
