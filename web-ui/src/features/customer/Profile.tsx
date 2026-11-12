import { useState } from 'react';
import type { FormEvent } from 'react';
import { Check } from 'lucide-react';
import { CustomerHeader } from './Catalog';
import type { User } from '../../shared/types';
import { request } from '../../shared/api';
import { useApp, useCommand, useQuery } from '../../shared/state';
import { Button, InfoNote, PageHeader, Panel, QueryState } from '../../shared/ui';
type ProfileData = {
  name: string;
  phone: string;
  email: string | null;
  address: string;
  version: number;
};
export function Profile() {
  const q = useQuery<ProfileData>('/auth/profile');
  return (
    <>
      <CustomerHeader />
      <main className="store-container customer-content">
        <PageHeader
          eyebrow="THÔNG TIN CỦA BẠN"
          title="Thông tin liên hệ"
          description="Cập nhật đầu mối liên hệ để cửa hàng hỗ trợ đúng người."
        />
        <Panel className="profile-panel">
          <QueryState loading={q.loading} error={q.error}>
            {q.data && <ProfileForm key={q.data.version} profile={q.data} />}
          </QueryState>
        </Panel>
      </main>
    </>
  );
}
function ProfileForm({ profile }: { profile: ProfileData }) {
  const command = useCommand(),
    { setUser } = useApp();
  const [name, setName] = useState(profile.name),
    [phone, setPhone] = useState(profile.phone),
    [email, setEmail] = useState(profile.email ?? ''),
    [address, setAddress] = useState(profile.address);
  async function submit(e: FormEvent) {
    e.preventDefault();
    const result = await command.run(
      '/auth/profile',
      { name, phone, email, address, version: profile.version },
      'Đã cập nhật thông tin liên hệ.',
    );
    if (result.ok) setUser(await request<User>('/auth/me'));
  }
  return (
    <form className="form-stack" onSubmit={submit}>
      <label className="field">
        <span>Họ và tên</span>
        <input
          required
          maxLength={120}
          autoComplete="name"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
      </label>
      <label className="field">
        <span>Số điện thoại</span>
        <input
          required
          type="tel"
          pattern="\+?[0-9]{8,15}"
          maxLength={15}
          autoComplete="tel"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
        />
      </label>
      <label className="field">
        <span>Email (nếu có)</span>
        <input
          type="email"
          maxLength={160}
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
      </label>
      <label className="field">
        <span>Địa chỉ liên hệ</span>
        <textarea
          required
          maxLength={400}
          rows={3}
          autoComplete="street-address"
          value={address}
          onChange={(e) => setAddress(e.target.value)}
        />
      </label>
      <InfoNote>
        Địa chỉ trong các đơn đã lập tiếp tục theo thỏa thuận cũ. Nếu cần đổi địa điểm nhận thiết bị
        của đơn, hãy gửi đề nghị đổi địa chỉ trong hồ sơ đơn đó.
      </InfoNote>
      <Button busy={command.busy} type="submit">
        <Check size={17} />
        Lưu thông tin liên hệ
      </Button>
    </form>
  );
}
