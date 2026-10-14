import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, getPages, uploadFile } from "./apiClient";

afterEach(() => {
  vi.restoreAllMocks();
});

function mockFetch(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      statusText: "mock",
      json: async () => body,
    }),
  );
}

describe("apiClient", () => {
  it("retorna o corpo em upload bem-sucedido", async () => {
    mockFetch(201, { fileId: "abc", pages: 3, meta: { filename: "x.pdf", sizeBytes: 10 } });
    const res = await uploadFile(new File(["x"], "x.pdf"));
    expect(res.fileId).toBe("abc");
    expect(res.pages).toBe(3);
  });

  it("lança ApiError com code do envelope em erro", async () => {
    mockFetch(404, { error: { code: "FILE_NOT_FOUND", message: "não achou" } });
    await expect(getPages("nope")).rejects.toMatchObject({
      name: "ApiError",
      code: "FILE_NOT_FOUND",
    });
  });

  it("ApiError expõe code e message", () => {
    const e = new ApiError("X", "msg");
    expect(e.code).toBe("X");
    expect(e.message).toBe("msg");
  });
});
