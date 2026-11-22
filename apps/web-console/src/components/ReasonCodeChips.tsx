import { AlertTriangle, ShieldAlert } from 'lucide-react';

interface ReasonCodeMeta {
  description: string;
  isFallback?: boolean;
}

const REASON_CODE_CATALOG: Record<string, ReasonCodeMeta> = {
  VELOCITY_SPIKE: {
    description: 'Elevado volume de transações em janela de 1 hora (velocity_1h).',
  },
  HIGH_TICKET_NEW_ACCOUNT: {
    description: 'Valor elevado para conta de pagador recém-criada.',
  },
  NIGHT_ANOMALY: {
    description: 'Transação realizada em faixa horária atípica de madrugada.',
  },
  FOREIGN_IP_HIGH_AMOUNT: {
    description: 'IP fora do país padrão (BR) combinado com ticket alto.',
  },
  ML_ANOMALY_DETECTED: {
    description: 'IsolationForest classificou o vetor de features como anomalia estatística.',
  },
  ML_HIGH_FRAUD_PROBABILITY: {
    description: 'RandomForestClassifier apontou alta probabilidade de fraude.',
  },
  RISK_ENGINE_UNAVAILABLE: {
    description:
      'Fallback Resilience4j acionado: risk-engine-ai indisponível ou em timeout (força MANUAL_REVIEW).',
    isFallback: true,
  },
};

interface ReasonCodeChipsProps {
  reasonCodes: string[];
}

export function ReasonCodeChips({ reasonCodes }: ReasonCodeChipsProps) {
  if (reasonCodes.length === 0) {
    return (
      <span className="inline-flex rounded-lg border border-slate-800 bg-slate-950 px-2.5 py-1 text-[11px] text-slate-400">
        Sem reasonCodes informados
      </span>
    );
  }

  return (
    <div className="flex flex-wrap gap-1.5">
      {reasonCodes.map((code) => {
        const meta = REASON_CODE_CATALOG[code];
        const isFallback = code === 'RISK_ENGINE_UNAVAILABLE' || Boolean(meta?.isFallback);

        return (
          <span
            key={code}
            title={meta?.description ?? code}
            className={[
              'inline-flex items-center gap-1.5 rounded-lg border px-2.5 py-1 font-mono text-[11px] font-semibold',
              isFallback
                ? 'border-amber-400/50 bg-amber-500/20 text-amber-200 shadow-sm shadow-amber-500/10'
                : 'border-slate-700 bg-slate-950/90 text-slate-200',
            ].join(' ')}
          >
            {isFallback ? (
              <AlertTriangle className="h-3.5 w-3.5 shrink-0 text-amber-300" />
            ) : (
              <ShieldAlert className="h-3 w-3 shrink-0 text-slate-400" />
            )}
            <span>{code}</span>
            {isFallback && (
              <span className="rounded bg-amber-400/20 px-1.5 py-0.5 font-sans text-[10px] font-bold uppercase tracking-wide text-amber-200">
                Fallback Resilience4j
              </span>
            )}
          </span>
        );
      })}
    </div>
  );
}
