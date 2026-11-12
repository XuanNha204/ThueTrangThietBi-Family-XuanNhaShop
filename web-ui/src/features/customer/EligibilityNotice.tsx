import type { Row } from '../../shared/types';
import { useQuery } from '../../shared/state';
import { InfoNote } from '../../shared/ui';
export function EligibilityNotice() {
  const q = useQuery<Row>('/catalog/eligibility-policy');
  if (q.error)
    return (
      <InfoNote>
        Chưa tải được điều kiện xác minh. Liên hệ cửa hàng để đối chiếu trước khi xác nhận đơn.
      </InfoNote>
    );
  if (!q.data?.version) return null;
  return (
    <div className="eligibility-notice">
      <InfoNote>
        <strong>{q.data.title}</strong>
        <p>
          {q.data.required
            ? 'Cửa hàng cần xác minh điều kiện trước khi xác nhận và bàn giao thiết bị.'
            : 'Chính sách tham khảo; chưa bật yêu cầu xác minh bắt buộc.'}
        </p>
        <details>
          <summary>Xem tiêu chí xác minh · phiên bản {q.data.version}</summary>
          <p className="preserve-lines">{q.data.criteria}</p>
        </details>
      </InfoNote>
    </div>
  );
}
