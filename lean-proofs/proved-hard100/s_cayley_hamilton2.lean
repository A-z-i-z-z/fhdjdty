import Mathlib

open Finset

theorem s_cayley_hamilton2 (A : Matrix (Fin 2) (Fin 2) ℝ) : A * A - Matrix.trace A • A + Matrix.det A • (1 : Matrix (Fin 2) (Fin 2) ℝ) = 0 := by
  ext i j
  fin_cases i <;> fin_cases j <;> simp [Matrix.mul_apply, Fin.sum_univ_two, Matrix.trace_fin_two, Matrix.det_fin_two, Matrix.one_apply, smul_eq_mul] <;> ring

#print axioms s_cayley_hamilton2
