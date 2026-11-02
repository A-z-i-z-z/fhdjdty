"""Verifier tests. They use the core-Lean lemmas so they run without Mathlib.

    python3 -m unittest discover -s lean-proofs/tests -v
"""
import shutil
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "swarm"))
import verify  # noqa: E402

Q = verify.load_queue()
HAVE_LEAN = shutil.which("lean") is not None


def ans(code: str) -> str:
    return f"Here is my proof.\n\n```lean\n{code.strip()}\n```\n"


GOOD_SUM_TO = """
theorem core_sum_to (n : Nat) : 2 * sumTo n = n * (n + 1) := by
  induction n with
  | zero => rfl
  | succ k ih =>
    simp only [sumTo, Nat.mul_add, ih, Nat.add_mul, Nat.mul_one, Nat.one_mul]
    omega
"""


class PolicyTests(unittest.TestCase):
    """Rejections that must happen before Lean is ever called."""

    def check(self, lemma_id, code, stage):
        r = verify.verify_answer(Q[lemma_id], ans(code))
        self.assertFalse(r.ok, r)
        self.assertEqual(r.stage, stage, r.errors)
        return r

    def test_no_code_block(self):
        r = verify.verify_answer(Q["core_three_dvd"], "I think it's true by induction.")
        self.assertEqual(r.stage, "parse")

    def test_changed_statement(self):
        self.check("core_three_dvd", "theorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + n := by omega", "parse")

    def test_weakened_statement_with_hypothesis(self):
        self.check("core_three_dvd", "theorem core_three_dvd (n : Nat) (h : n = 0) : 3 ∣ n ^ 3 + 2 * n := by simp [h]", "parse")

    def test_sorry(self):
        self.check("core_three_dvd", "theorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by sorry", "policy")

    def test_axiom_helper(self):
        self.check("core_three_dvd", "axiom cheat : False\ntheorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := cheat.elim", "policy")

    def test_native_decide(self):
        self.check("core_three_dvd", "theorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by native_decide", "policy")

    def test_instance_redefinition(self):
        code = "instance : HPow Nat Nat Nat := ⟨fun _ _ => 0⟩\ntheorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by omega"
        self.check("core_three_dvd", code, "policy")

    def test_open_namespace_before_statement(self):
        self.check("core_three_dvd", "open Nat\ntheorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by omega", "policy")

    def test_exit_command(self):
        self.check("core_three_dvd", "theorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by\n  omega\n#exit", "policy")

    def test_extra_top_level_command_after_proof(self):
        code = "theorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by\n  omega\ntheorem x : True := trivial"
        self.check("core_three_dvd", code, "policy")

    def test_debug_option(self):
        code = "theorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by\n  set_option debug.skipKernelTC true in omega"
        self.check("core_three_dvd", code, "policy")


@unittest.skipUnless(HAVE_LEAN, "lean not on PATH")
class CompileTests(unittest.TestCase):

    def test_good_proof_with_preamble(self):
        r = verify.verify_answer(Q["core_sum_to"], ans(GOOD_SUM_TO))
        self.assertTrue(r.ok, r.errors)
        self.assertTrue(set(r.axioms) <= verify.ALLOWED_AXIOMS)

    def test_agent_echoes_preamble(self):
        code = Q["core_sum_to"]["preamble"] + "\n" + GOOD_SUM_TO
        r = verify.verify_answer(Q["core_sum_to"], ans(code))
        self.assertTrue(r.ok, r.errors)

    def test_good_proof_with_helper_lemma(self):
        code = """
theorem helper_mod (n : Nat) : (n ^ 3 + 2 * n) % 3 = 0 := by
  have h : n % 3 = 0 ∨ n % 3 = 1 ∨ n % 3 = 2 := by omega
  rcases h with h | h | h <;> simp [Nat.pow_succ, Nat.add_mod, Nat.mul_mod, h]

theorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by
  exact Nat.dvd_of_mod_eq_zero (helper_mod n)
"""
        r = verify.verify_answer(Q["core_three_dvd"], ans(code))
        self.assertTrue(r.ok, r.errors)

    def test_library_lemma(self):
        code = "theorem core_mul_mod (a b n : Nat) : (a * b) % n = ((a % n) * (b % n)) % n := Nat.mul_mod a b n"
        r = verify.verify_answer(Q["core_mul_mod"], ans(code))
        self.assertTrue(r.ok, r.errors)

    def test_wrong_proof_fails_compile(self):
        code = "theorem core_three_dvd (n : Nat) : 3 ∣ n ^ 3 + 2 * n := by\n  simp"
        r = verify.verify_answer(Q["core_three_dvd"], ans(code))
        self.assertFalse(r.ok)
        self.assertEqual(r.stage, "compile")
        self.assertTrue(r.errors)

    def test_core_statements_elaborate(self):
        for lem in Q.values():
            if lem["requires"] == "core":
                r = verify.check_statement(lem)
                self.assertTrue(r.ok, (lem["id"], r.errors))


if __name__ == "__main__":
    unittest.main()
