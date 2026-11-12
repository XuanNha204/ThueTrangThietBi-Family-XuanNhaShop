import { useState } from 'react';
import type { FormEvent } from 'react';
import { Check } from 'lucide-react';
import type { Equipment } from '../../shared/types';
import { useCommand, useQuery } from '../../shared/state';
import { Button, Dialog, InfoNote, QueryState } from '../../shared/ui';
export type Checklist = {
  typeId: string;
  version: number;
  checks: { code: string; label: string; blocking: boolean }[];
};

export function InspectionDialog({
  equipment,
  planId,
  onClose,
}: {
  equipment: Pick<Equipment, 'id' | 'type_id' | 'code' | 'name'>;
  planId?: string;
  onClose: () => void;
}) {
  const cmd = useCommand(),
    q = useQuery<Checklist>(`/maintenance/checklists/${equipment.type_id}`);
  const [safe, setSafe] = useState(false),
    [clean, setClean] = useState(false),
    [complete, setComplete] = useState(false),
    [findings, setFindings] = useState('');
  const [answers, setAnswers] = useState<Record<string, { passed: string; note: string }>>({});
  const [error, setError] = useState('');
  const passed =
    safe &&
    clean &&
    complete &&
    !!q.data &&
    q.data.checks.filter((c) => c.blocking).every((c) => answers[c.code]?.passed === 'true');
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!q.data || cmd.busy) return;
    setError('');
    const result = await cmd.run(
      planId ? `/maintenance/plans/${planId}/complete` : `/equipment/${equipment.id}/inspection`,
      {
        powerSafe: safe,
        clean,
        complete,
        findings,
        checklistVersion: q.data.version,
        answers: q.data.checks.map((c) => ({
          code: c.code,
          passed: answers[c.code]?.passed === 'true',
          note: answers[c.code]?.note || '',
        })),
      },
      passed
        ? 'Đã lưu kiểm tra đạt; thiết bị sẵn sàng.'
        : 'Đã lưu kiểm tra; thiết bị vẫn cần xử lý kỹ thuật.',
    );
    if (result.ok) onClose();
    else setError(result.error || 'Chưa lưu được kết quả.');
  }
  return (
    <Dialog
      title={planId ? `Kết thúc bảo trì ${equipment.code}` : 'Kiểm tra an toàn thiết bị'}
      description={`${equipment.code} · ${equipment.name}`}
      onClose={() => {
        if (!cmd.busy) onClose();
      }}
      showToastError={false}
    >
      <QueryState loading={q.loading} error={q.error}>
        <form className="form-stack" onSubmit={submit}>
          {error && (
            <p role="alert" className="inline-error">
              {error}
            </p>
          )}
          <fieldset className="checklist">
            <legend>Đối chiếu trực tiếp trước khi đánh dấu</legend>
            {[
              [safe, setSafe, 'Vận hành và kết cấu an toàn'],
              [clean, setClean, 'Đã làm sạch, vệ sinh phù hợp'],
              [complete, setComplete, 'Đủ phụ kiện và đã đối chiếu'],
            ].map(([value, setter, label], i) => (
              <label className="checkbox-field" key={i}>
                <input
                  type="checkbox"
                  checked={value as boolean}
                  onChange={(e) => (setter as (v: boolean) => void)(e.target.checked)}
                />
                <span>{label as string}</span>
              </label>
            ))}
          </fieldset>
          {!!q.data?.checks.length && (
            <fieldset className="inspection-extra">
              <legend>Tiêu chí theo loại · phiên bản {q.data.version}</legend>
              {q.data.checks.map((c) => (
                <div className="inspection-criterion" key={c.code}>
                  <label className="field">
                    <span id={`criterion-${c.code}`}>{c.label}</span>
                    <select
                      aria-labelledby={`criterion-${c.code}`}
                      aria-describedby={`criterion-help-${c.code}`}
                      required
                      value={answers[c.code]?.passed || ''}
                      onChange={(e) =>
                        setAnswers((old) => ({
                          ...old,
                          [c.code]: { note: old[c.code]?.note || '', passed: e.target.value },
                        }))
                      }
                    >
                      <option value="">Chọn kết quả thực tế</option>
                      <option value="true">Đạt</option>
                      <option value="false">Chưa đạt</option>
                    </select>
                    <small id={`criterion-help-${c.code}`}>
                      {c.blocking
                        ? 'Bắt buộc đạt để đưa thiết bị vào khai thác.'
                        : 'Tiêu chí theo dõi; chưa đạt phải ghi rõ lưu ý.'}
                    </small>
                  </label>
                  <label className="field">
                    <span>Ghi chú: {c.label}</span>
                    <textarea
                      rows={2}
                      maxLength={500}
                      required={answers[c.code]?.passed === 'false'}
                      value={answers[c.code]?.note || ''}
                      onChange={(e) =>
                        setAnswers((old) => ({
                          ...old,
                          [c.code]: { passed: old[c.code]?.passed || '', note: e.target.value },
                        }))
                      }
                    />
                  </label>
                </div>
              ))}
            </fieldset>
          )}
          <label className="field">
            <span>Kết quả, căn cứ kiểm tra và lưu ý</span>
            <textarea
              required
              rows={4}
              maxLength={1000}
              value={findings}
              onChange={(e) => setFindings(e.target.value)}
            />
          </label>
          <InfoNote>
            {passed
              ? 'Thiết bị sẽ chuyển sang Sẵn sàng. Các lịch bảo trì còn hiệu lực vẫn chặn việc cấp lịch.'
              : 'Nếu chưa đạt đủ tiêu chí bắt buộc, thiết bị chuyển sang Bảo trì và không được cấp lịch thuê mới.'}
          </InfoNote>
          <div className="dialog-actions">
            <Button type="button" className="secondary" disabled={cmd.busy} onClick={onClose}>
              Quay lại
            </Button>
            <Button busy={cmd.busy} disabled={!q.data} type="submit">
              <Check size={16} />
              {planId ? 'Lưu kết quả bảo trì' : 'Lưu kiểm tra'}
            </Button>
          </div>
        </form>
      </QueryState>
    </Dialog>
  );
}
