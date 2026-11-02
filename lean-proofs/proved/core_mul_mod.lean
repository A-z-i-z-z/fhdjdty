
theorem core_mul_mod (a b n : Nat) : (a * b) % n = ((a % n) * (b % n)) % n := by
  first | exact Nat.mul_mod a b n | simp [Nat.mul_mod]

#print axioms core_mul_mod
