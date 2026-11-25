/* Lucian UX/UI Prompt #290 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#290 */
import { useState } from 'react';
import './lucian-290.css';

interface LucianReminderPrimeCardProps {
  permission: NotificationPermission | 'unsupported';
  onEnable: () => Promise<void>;
}

export function LucianReminderPrimeCard({ permission, onEnable }: LucianReminderPrimeCardProps) {
  const [dismissed, setDismissed] = useState(false);

  if (permission === 'unsupported' || permission === 'granted' || dismissed) return null;

  return (
    <div className="ux u290-bar animate-memora-enter">
      <div className="u290-left">
        <div className="u290-bl" aria-hidden="true">
          <svg viewBox="0 0 28 28">
            <path
              d="M7 19v-7a7 7 0 0 1 14 0v7l2 2.5H5zM11.5 24a2.6 2.6 0 0 0 5 0"
              fill="none"
              stroke="currentColor"
              strokeWidth="2.2"
              strokeLinejoin="round"
              strokeLinecap="round"
            />
          </svg>
        </div>
        <div className="u290-copy">
          <h4>Want a heads-up before detected deadlines &amp; exams?</h4>
          <p>Only reminders you confirm — zero spam, turn off anytime.</p>
        </div>
      </div>

      <div className="u290-actions">
        <button
          type="button"
          className="u290-lt memora-press"
          onClick={() => setDismissed(true)}
        >
          Later
        </button>
        <button
          type="button"
          className="u290-ok memora-press"
          onClick={() => void onEnable()}
        >
          Turn on alerts
        </button>
      </div>
    </div>
  );
}
