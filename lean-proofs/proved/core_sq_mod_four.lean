
theorem core_sq_mod_four (n : Nat) : n ^ 2 % 4 = 0 ∨ n ^ 2 % 4 = 1 := by
  have h : n ^ 2 % 4 = (n % 4) ^ 2 % 4 := Nat.pow_mod n 2 4
  have hc : n % 4 = 0 ∨ n % 4 = 1 ∨ n % 4 = 2 ∨ n % 4 = 3 := by omega
  rw [h]
  rcases hc with h1 | h1 | h1 | h1 <;> rw [h1] <;> decide

#print axioms core_sq_mod_four
