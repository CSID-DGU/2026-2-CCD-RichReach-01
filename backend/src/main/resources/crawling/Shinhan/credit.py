"""shinhan credit: run with --help for modes and output options."""
import sys
from pathlib import Path
if __package__ in (None, ''):
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from _shared.entry_cli import run

if __name__ == '__main__':
    run('shinhan', 'credit')
