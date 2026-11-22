import { ChevronLeft, ChevronRight } from 'lucide-react';

interface PaginationControlsProps {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  isLoading?: boolean;
  onPageChange: (nextPage: number) => void;
  onSizeChange?: (nextSize: number) => void;
}

export function PaginationControls({
  page,
  size,
  totalElements,
  totalPages,
  isLoading = false,
  onPageChange,
  onSizeChange,
}: PaginationControlsProps) {
  const safeTotalPages = Math.max(totalPages, 1);
  const hasPrevious = page > 0;
  const hasNext = page + 1 < totalPages;

  return (
    <div className="flex flex-col justify-between gap-3 border-t border-slate-800/80 px-4 py-3 text-xs text-slate-400 sm:flex-row sm:items-center">
      <div className="flex flex-wrap items-center gap-3">
        <span>
          Total de registros: <strong className="font-mono text-slate-200">{totalElements}</strong>
        </span>
        {onSizeChange && (
          <label className="inline-flex items-center gap-2">
            <span>Itens/página:</span>
            <select
              value={size}
              disabled={isLoading}
              onChange={(e) => onSizeChange(Number(e.target.value))}
              className="rounded-lg border border-slate-700 bg-slate-950 px-2 py-1 font-mono text-xs text-slate-200 focus:border-emerald-500 focus:outline-none"
            >
              {[10, 20, 50].map((optionSize) => (
                <option key={optionSize} value={optionSize}>
                  {optionSize}
                </option>
              ))}
            </select>
          </label>
        )}
      </div>

      <div className="flex items-center gap-2">
        <span className="font-mono text-slate-300">
          Página {totalElements === 0 ? 0 : page + 1} de {totalElements === 0 ? 0 : safeTotalPages}
        </span>
        <button
          type="button"
          disabled={!hasPrevious || isLoading}
          onClick={() => onPageChange(page - 1)}
          aria-label="Página anterior"
          className="inline-flex items-center gap-1 rounded-lg border border-slate-800 bg-slate-950 px-2.5 py-1.5 font-medium text-slate-300 transition-colors hover:border-slate-700 hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          <ChevronLeft className="h-4 w-4" />
          <span>Anterior</span>
        </button>
        <button
          type="button"
          disabled={!hasNext || isLoading}
          onClick={() => onPageChange(page + 1)}
          aria-label="Próxima página"
          className="inline-flex items-center gap-1 rounded-lg border border-slate-800 bg-slate-950 px-2.5 py-1.5 font-medium text-slate-300 transition-colors hover:border-slate-700 hover:text-white disabled:cursor-not-allowed disabled:opacity-40"
        >
          <span>Próxima</span>
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
