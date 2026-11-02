
def fib : Nat → Nat
  | 0 => 0
  | 1 => 1
  | n + 2 => fib n + fib (n + 1)

def fibSum : Nat → Nat
  | 0 => 0
  | n + 1 => fib n + fibSum n

theorem core_fib_sum (n : Nat) : fibSum n + 1 = fib (n + 1) := by
  induction n with
  | zero => decide
  | succ k ih =>
    have h1 : fibSum (k + 1) = fib k + fibSum k := rfl
    have h2 : fib (k + 1 + 1) = fib k + fib (k + 1) := rfl
    rw [h1, h2]
    omega

#print axioms core_fib_sum
