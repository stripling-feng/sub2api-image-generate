import type { Request, Response, NextFunction } from "express";
import { nanoid } from "nanoid";
import { prisma } from "./db.js";
import { hashSecret } from "./crypto.js";
import { config } from "./config.js";

export const sessionCookie = "sub2api_session";

export async function createSession(profileId: string, res: Response): Promise<void> {
  const token = nanoid(48);
  const expiresAt = new Date(Date.now() + config.sessionDays * 24 * 60 * 60 * 1000);

  await prisma.apiSession.create({
    data: {
      tokenHash: hashSecret(token),
      profileId,
      expiresAt
    }
  });

  res.cookie(sessionCookie, token, {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    expires: expiresAt
  });
}

export type AuthedRequest = Request & {
  profile: {
    id: string;
    baseUrl: string;
    keyHash: string;
    encryptedKey: string;
  };
};

export async function requireSession(req: Request, res: Response, next: NextFunction): Promise<void> {
  try {
    const apiKey = req.header("X-API-Key");
    if (apiKey) {
      const profile = await prisma.apiProfile.findUnique({
        where: { keyHash: hashSecret(apiKey) }
      });
      if (!profile) {
        res.status(401).json({ error: "API Key has not been saved. Bind it first." });
        return;
      }
      (req as AuthedRequest).profile = profile;
      next();
      return;
    }

    const token = req.cookies?.[sessionCookie];
    if (!token) {
      res.status(401).json({ error: "Not bound. Enter your sub2api URL and API Key first." });
      return;
    }

    const session = await prisma.apiSession.findUnique({
      where: { tokenHash: hashSecret(token) },
      include: { profile: true }
    });

    if (!session || session.expiresAt.getTime() < Date.now()) {
      if (session) {
        await prisma.apiSession.delete({ where: { id: session.id } }).catch(() => undefined);
      }
      res.clearCookie(sessionCookie);
      res.status(401).json({ error: "Session expired. Please bind your API Key again." });
      return;
    }

    (req as AuthedRequest).profile = session.profile;
    next();
  } catch (error) {
    next(error);
  }
}
