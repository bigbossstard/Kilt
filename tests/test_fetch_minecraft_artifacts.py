from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from tools.fetch_minecraft_artifacts import sha1_file


class MinecraftArtifactFetcherTests(unittest.TestCase):
    def test_sha1_file_matches_known_digest(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "sample.bin"
            path.write_bytes(b"abc")
            self.assertEqual(sha1_file(path), "a9993e364706816aba3e25717850c26c9cd0d89d")


if __name__ == "__main__":
    unittest.main()
