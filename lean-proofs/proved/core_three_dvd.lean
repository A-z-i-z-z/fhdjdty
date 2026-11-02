
theorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by
  apply Nat.dvd_of_mod_eq_zero
  rw [Nat.add_mod, Nat.pow_mod, Nat.mul_mod]
  rcases (by omega : n % 3 = 0 ∨ n % 3 = 1 ∨ n % 3 = 2) with h0 | h0 | h0 <;> simp [h0]

#print axioms core_three_dvd
