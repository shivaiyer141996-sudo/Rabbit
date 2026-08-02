import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LoginForm } from "./login-form";

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
    refresh: vi.fn(),
  }),
}));

afterEach(cleanup);

describe("LoginForm pilot safety", () => {
  it("does not expose or prefill seeded demo credentials", () => {
    render(<LoginForm />);

    expect(screen.getByLabelText("Email address")).toHaveProperty("value", "");
    expect(screen.getByLabelText("Password")).toHaveProperty("value", "");
    expect(screen.queryByText(/Rabbit@123/i)).toBeNull();
    expect(screen.queryByText("Keep me signed in")).toBeNull();
  });
});
